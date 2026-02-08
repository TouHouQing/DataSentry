package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.enums.CleaningCostChannel;
import com.touhouqing.datasentry.dto.ModelConfigDTO;
import com.touhouqing.datasentry.enums.ModelType;
import com.touhouqing.datasentry.service.aimodelconfig.ModelConfigDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 成本追踪服务 提供简单直接的成本记录方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCostTrackingService {

	private final CleaningCostLedgerService costLedgerService;

	private final CleaningPricingService pricingService;

	private final ModelConfigDataService modelConfigDataService;

	// 存储 threadId 到 agentId 的映射
	private final Map<String, Long> threadAgentMap = new ConcurrentHashMap<>();

	/**
	 * 注册会话与智能体的关联
	 */
	public void registerSession(String threadId, Long agentId) {
		if (threadId != null && agentId != null) {
			threadAgentMap.put(threadId, agentId);
			log.debug("Registered session tracking: threadId={}, agentId={}", threadId, agentId);
		}
	}

	/**
	 * 取消注册会话
	 */
	public void unregisterSession(String threadId) {
		if (threadId != null) {
			threadAgentMap.remove(threadId);
			log.debug("Unregistered session tracking: threadId={}", threadId);
		}
	}

	/**
	 * 追踪对话模型成本 (自动获取上下文)
	 */
	public void trackChatCost(ChatResponse response) {
		com.touhouqing.datasentry.cleaning.context.AiCostContextHolder.RequestContext context = com.touhouqing.datasentry.cleaning.context.AiCostContextHolder
			.getContext();

		if (context == null) {
			// 如果没有上下文，尝试从 Aspect 获取 (兼容旧代码)
			var aspectContext = com.touhouqing.datasentry.cleaning.aspect.AiCostTrackingAspect.getContext();
			if (aspectContext != null) {
				trackChatCost(aspectContext.threadId(), response);
				return;
			}
			log.debug("No context found for cost tracking");
			return;
		}

		trackChatCost(context.threadId(), response);
	}

	/**
	 * 追踪对话模型成本
	 */
	public void trackChatCost(String threadId, ChatResponse response) {
		log.info("🔍 CostTracking: trackChatCost called for threadId: {}", threadId);
		try {
			Long agentId = null;
			// 1. 优先从 ContextHolder 获取
			var context = com.touhouqing.datasentry.cleaning.context.AiCostContextHolder.getContext();
			if (context != null && context.threadId().equals(threadId)) {
				agentId = context.agentId();
				log.info("🔍 CostTracking: Found agentId {} in ContextHolder", agentId);
			}

			// 2. 尝试从 Aspect Context 获取 (Fix for mixed usage)
			if (agentId == null) {
				var aspectContext = com.touhouqing.datasentry.cleaning.aspect.AiCostTrackingAspect.getContext();
				if (aspectContext != null && aspectContext.threadId().equals(threadId)) {
					agentId = aspectContext.agentId();
					log.info("🔍 CostTracking: Found agentId {} in AspectContext", agentId);
				}
			}

			// 3. 如果没有，从 threadAgentMap 获取
			if (agentId == null) {
				agentId = threadAgentMap.get(threadId);
				if (agentId != null) {
					log.info("🔍 CostTracking: Found agentId {} in threadAgentMap", agentId);
				}
			}

			if (agentId == null) {
				log.warn(
						"❌ Cost Tracking Failed: No agentId found for threadId: {}. Context missing and not in registry.",
						threadId);
				return;
			}

			if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
				log.warn("⚠️ Cost Tracking: No usage metadata in ChatResponse for threadId: {}", threadId);
				return;
			}

			var usage = response.getMetadata().getUsage();
			long inputTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
			long outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

			log.info("🔍 CostTracking: Tokens - Input: {}, Output: {}", inputTokens, outputTokens);

			if (inputTokens == 0 && outputTokens == 0) {
				log.warn("⚠️ Cost Tracking: Zero tokens for threadId: {}", threadId);
				return;
			}

			// 获取模型配置
			ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
			if (config == null) {
				log.warn("❌ Cost Tracking: No active CHAT model config found");
				return;
			}

			log.info("🔍 CostTracking: Model Config - Provider: {}, Model: {}", config.getProvider(),
					config.getModelName());

			String provider = config.getProvider();
			String model = config.getModelName();

			// 获取价格
			CleaningPricingService.Pricing pricing = pricingService.resolvePricing(provider, model);

			log.info("💰 Resolved Pricing for CHAT [{}]: Input {} {}/1k, Output {} {}/1k", model,
					pricing.inputPricePer1k(), pricing.currency(), pricing.outputPricePer1k(), pricing.currency());

			// 记录成本
			CleaningCostLedgerService.CostEntry entry = new CleaningCostLedgerService.CostEntry(null, // jobId
					null, // jobRunId
					agentId, // agentId
					threadId, // traceId
					CleaningCostChannel.ANALYSIS, // channel
					"CHAT", // detectorLevel
					provider, // provider
					model, // model
					inputTokens, // inputTokensEst
					outputTokens, // outputTokensEst
					pricing.inputPricePer1k(), // unitPriceIn
					pricing.outputPricePer1k(), // unitPriceOut
					pricing.currency() // currency
			);

			BigDecimal cost = costLedgerService.recordCost(entry);
			log.info("✅ Tracked CHAT cost for thread {}: {} {} (input={}, output={})", threadId, cost,
					pricing.currency(), inputTokens, outputTokens);

		}
		catch (Exception e) {
			log.error("Failed to track chat cost for threadId {}: {}", threadId, e.getMessage(), e);
		}
	}

	/**
	 * 追踪向量模型成本
	 */
	public void trackEmbeddingCost(String threadId, EmbeddingResponse response) {
		try {
			Long agentId = null;
			// 1. 优先从 ContextHolder 获取
			var context = com.touhouqing.datasentry.cleaning.context.AiCostContextHolder.getContext();
			if (context != null && context.threadId().equals(threadId)) {
				agentId = context.agentId();
			}

			// 2. 尝试从 Aspect Context 获取
			if (agentId == null) {
				var aspectContext = com.touhouqing.datasentry.cleaning.aspect.AiCostTrackingAspect.getContext();
				if (aspectContext != null && aspectContext.threadId().equals(threadId)) {
					agentId = aspectContext.agentId();
				}
			}

			// 3. 如果没有，从 threadAgentMap 获取
			if (agentId == null) {
				agentId = threadAgentMap.get(threadId);
			}

			if (agentId == null) {
				log.warn("❌ Cost Tracking Failed (Embedding): No agentId found for threadId: {}", threadId);
				return;
			}

			if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
				log.debug("No usage metadata in EmbeddingResponse for threadId: {}", threadId);
				return;
			}

			var usage = response.getMetadata().getUsage();
			long inputTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;

			if (inputTokens == 0) {
				log.debug("Zero tokens for embedding in threadId: {}", threadId);
				return;
			}

			// 获取模型配置
			ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);
			if (config == null) {
				log.warn("No active EMBEDDING model config found, skipping cost tracking");
				return;
			}

			String provider = config.getProvider();
			String model = config.getModelName();

			// 获取价格
			CleaningPricingService.Pricing pricing = pricingService.resolvePricing(provider, model);

			log.info("💰 Resolved Pricing for EMBEDDING [{}]: Input {} {}/1k", model, pricing.inputPricePer1k(),
					pricing.currency());

			// 记录成本（向量模型只有输入成本）
			CleaningCostLedgerService.CostEntry entry = new CleaningCostLedgerService.CostEntry(null, // jobId
					null, // jobRunId
					agentId, // agentId
					threadId, // traceId
					CleaningCostChannel.ANALYSIS, // channel
					"EMBEDDING", // detectorLevel
					provider, // provider
					model, // model
					inputTokens, // inputTokensEst
					0L, // outputTokensEst
					pricing.inputPricePer1k(), // unitPriceIn
					BigDecimal.ZERO, // unitPriceOut
					pricing.currency() // currency
			);

			BigDecimal cost = costLedgerService.recordCost(entry);
			log.info("✅ Tracked EMBEDDING cost for thread {}: {} {} (tokens={})", threadId, cost, pricing.currency(),
					inputTokens);

		}
		catch (Exception e) {
			log.error("Failed to track embedding cost for threadId {}: {}", threadId, e.getMessage(), e);
		}
	}

}
