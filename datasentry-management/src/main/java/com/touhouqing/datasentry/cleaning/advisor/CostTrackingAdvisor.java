package com.touhouqing.datasentry.cleaning.advisor;

import com.touhouqing.datasentry.cleaning.context.AiCostContextHolder;
import com.touhouqing.datasentry.cleaning.service.AiCostTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class CostTrackingAdvisor implements CallAdvisor, StreamAdvisor {

    private final AiCostTrackingService costTrackingService;

    @Override
    public String getName() {
        return "CostTrackingAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);

        try {
            AiCostContextHolder.RequestContext context = AiCostContextHolder.getContext();
            if (context != null) {
                costTrackingService.trackChatCost(context.threadId(), response.chatResponse());
            } else {
                costTrackingService.trackChatCost(response.chatResponse());
            }
        } catch (Exception e) {
            log.error("Failed to track cost in advisor", e);
        }

        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // Capture context at assembly time (when .stream() is called)
        AiCostContextHolder.RequestContext context = AiCostContextHolder.getContext();
        String capturedThreadId = context != null ? context.threadId() : null;

        if (capturedThreadId == null) {
            log.warn("⚠️ CostTrackingAdvisor: No context captured at stream assembly time! Thread: {}", Thread.currentThread().getName());
        } else {
            log.info("✅ CostTrackingAdvisor: Captured threadId {} at stream assembly. Thread: {}", capturedThreadId, Thread.currentThread().getName());
        }

        return chain.nextStream(request).doOnNext(response -> {
            try {
                if (capturedThreadId != null) {
                    costTrackingService.trackChatCost(capturedThreadId, response.chatResponse());
                } else {
                    log.warn("⚠️ CostTrackingAdvisor: Trying fallback tracking (no threadId). Thread: {}", Thread.currentThread().getName());
                    costTrackingService.trackChatCost(response.chatResponse());
                }
            } catch (Exception e) {
                log.error("Failed to track cost in advisor (stream)", e);
            }
        });
    }
}
