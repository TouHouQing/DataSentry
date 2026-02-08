package com.touhouqing.datasentry.cleaning.aspect;

import com.touhouqing.datasentry.cleaning.enums.CleaningCostChannel;
import com.touhouqing.datasentry.cleaning.service.CleaningCostLedgerService;
import com.touhouqing.datasentry.cleaning.service.CleaningPricingService;
import com.touhouqing.datasentry.dto.ModelConfigDTO;
import com.touhouqing.datasentry.enums.ModelType;
import com.touhouqing.datasentry.service.aimodelconfig.ModelConfigDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;

/**
 * AI Cost Tracking Aspect
 * Intercepts BlockLlmService (Chat) and EmbeddingModel (Vector) calls.
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class AiCostTrackingAspect {

    private final CleaningCostLedgerService costLedgerService;
    private final CleaningPricingService pricingService;
    private final ModelConfigDataService modelConfigDataService;

    // Use InheritableThreadLocal to support child threads in the graph execution
    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT = new InheritableThreadLocal<>();

    // -------------------------------------------------------------------------
    // 1. Intercept Chat calls (BlockLlmService)
    // -------------------------------------------------------------------------
    // @Around("execution(* com.touhouqing.datasentry.service.llm.impls.BlockLlmService.call*(..))")
    public Object trackChatServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        // Capture context at the moment of invocation
        RequestContext context = REQUEST_CONTEXT.get();

        Object result = joinPoint.proceed();

        if (context != null && result instanceof Flux<?> flux) {
            // Hook into the Flux stream to record cost when response arrives
            return ((Flux<ChatResponse>) flux).doOnNext(response -> trackChatCost(context, response));
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // 2. Intercept Embedding calls (EmbeddingModel)
    // -------------------------------------------------------------------------
    @Around("execution(* org.springframework.ai.embedding.EmbeddingModel.call(..)) && args(request)")
    public Object trackEmbeddingCall(ProceedingJoinPoint joinPoint, EmbeddingRequest request) throws Throwable {
        RequestContext context = REQUEST_CONTEXT.get();

        Object result = joinPoint.proceed();

        if (context != null && result instanceof EmbeddingResponse response) {
            trackEmbeddingCost(context, response);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Logic
    // -------------------------------------------------------------------------

    private void trackChatCost(RequestContext context, ChatResponse response) {
        try {
            if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return;
            }

            var usage = response.getMetadata().getUsage();
            long inputTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            // Use getCompletionTokens() as verified in previous step
            long outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

            if (inputTokens == 0 && outputTokens == 0) return;

            ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
            if (config == null) return;

            recordCost(context, "CHAT", config.getProvider(), config.getModelName(), inputTokens, outputTokens);
        } catch (Exception e) {
            log.error("Failed to track chat cost", e);
        }
    }

    private void trackEmbeddingCost(RequestContext context, EmbeddingResponse response) {
        try {
            if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return;
            }

            var usage = response.getMetadata().getUsage();
            long inputTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;

            if (inputTokens == 0) return;

            ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);
            if (config == null) return;

            recordCost(context, "EMBEDDING", config.getProvider(), config.getModelName(), inputTokens, 0);
        } catch (Exception e) {
            log.error("Failed to track embedding cost", e);
        }
    }

    private void recordCost(RequestContext context, String level, String provider, String model, long input, long output) {
        CleaningPricingService.Pricing pricing = pricingService.resolvePricing(provider, model);

        CleaningCostLedgerService.CostEntry entry = new CleaningCostLedgerService.CostEntry(
                null, null,
                context.agentId(),
                context.threadId(),
                CleaningCostChannel.ANALYSIS,
                level,
                provider, model,
                input, output,
                pricing.inputPricePer1k(),
                pricing.outputPricePer1k(),
                pricing.currency()
        );

        BigDecimal cost = costLedgerService.recordCost(entry);
        log.info("💰 Cost Recorded [{}]: {} {} (In: {}, Out: {})", level, cost, pricing.currency(), input, output);
    }

    public static void setContext(String threadId, Long agentId) {
        REQUEST_CONTEXT.set(new RequestContext(threadId, agentId));
    }

    public static void clearContext() {
        REQUEST_CONTEXT.remove();
    }

    public static RequestContext getContext() {
        return REQUEST_CONTEXT.get();
    }

    public record RequestContext(String threadId, Long agentId) {}
}
