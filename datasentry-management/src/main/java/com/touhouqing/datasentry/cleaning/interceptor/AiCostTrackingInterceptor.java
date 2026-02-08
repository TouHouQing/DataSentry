package com.touhouqing.datasentry.cleaning.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.touhouqing.datasentry.cleaning.enums.CleaningCostChannel;
import com.touhouqing.datasentry.cleaning.service.CleaningCostLedgerService;
import com.touhouqing.datasentry.cleaning.service.CleaningPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 成本追踪拦截器
 * 自动追踪对话模型和向量模型的 token 使用量和成本
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCostTrackingInterceptor {

	private final CleaningCostLedgerService costLedgerService;

	private final CleaningPricingService pricingService;

	private final ObjectMapper objectMapper;

	// 存储会话ID到AgentID的映射
	private final Map<String, Long> sessionAgentMap = new ConcurrentHashMap<>();

	/**
	 * 注册会话与智能体的关联
	 */
	public void registerSession(String sessionId, Long agentId) {
		if (sessionId != null && agentId != null) {
			sessionAgentMap.put(sessionId, agentId);
		}
	}

	/**
	 * 追踪对话模型成本
	 * @param sessionId 会话ID
	 * @param prompt 请求提示词
	 * @param response AI响应
	 * @param provider 模型提供方
	 * @param model 模型名称
	 */
	public UsageMetadata trackChatCost(String sessionId, Prompt prompt, ChatResponse response, String provider,
			String model) {
		try {
			Long agentId = sessionAgentMap.get(sessionId);
			if (agentId == null) {
				log.debug("No agentId found for session {}, skipping cost tracking", sessionId);
				return null;
			}

			// 提取 token 使用量
			long inputTokens = 0;
			long outputTokens = 0;

			if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
				var usage = response.getMetadata().getUsage();
				inputTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
				outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
			}

			if (inputTokens == 0 && outputTokens == 0) {
				log.debug("No token usage found for session {}", sessionId);
				return null;
			}

			// 获取价格
			CleaningPricingService.Pricing pricing = pricingService.resolvePricing(provider, model);

			// 记录成本
			CleaningCostLedgerService.CostEntry entry = new CleaningCostLedgerService.CostEntry(null, // jobId
					null, // jobRunId
					agentId, // agentId
					sessionId, // traceId
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
			log.info("Tracked chat cost for session {}: {} {}", sessionId, cost, pricing.currency());

			// 返回 usage metadata 用于前端保存
			return new UsageMetadata(provider, model, inputTokens, outputTokens);

		}
		catch (Exception e) {
			log.error("Failed to track chat cost for session {}: {}", sessionId, e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 追踪向量模型成本
	 * @param sessionId 会话ID
	 * @param request 向量请求
	 * @param response 向量响应
	 * @param provider 模型提供方
	 * @param model 模型名称
	 */
	public void trackEmbeddingCost(String sessionId, EmbeddingRequest request, EmbeddingResponse response,
			String provider, String model) {
		try {
			Long agentId = sessionAgentMap.get(sessionId);
			if (agentId == null) {
				log.debug("No agentId found for session {}, skipping embedding cost tracking", sessionId);
				return;
			}

			// 向量模型通常只计算输入 token
			long inputTokens = 0;
			if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
				var usage = response.getMetadata().getUsage();
				inputTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
			}

			if (inputTokens == 0) {
				log.debug("No token usage found for embedding in session {}", sessionId);
				return;
			}

			// 获取价格
			CleaningPricingService.Pricing pricing = pricingService.resolvePricing(provider, model);

			// 记录成本（向量模型只有输入成本）
			CleaningCostLedgerService.CostEntry entry = new CleaningCostLedgerService.CostEntry(null, // jobId
					null, // jobRunId
					agentId, // agentId
					sessionId, // traceId
					CleaningCostChannel.ANALYSIS, // channel
					"EMBEDDING", // detectorLevel
					provider, // provider
					model, // model
					inputTokens, // inputTokensEst
					0L, // outputTokensEst (向量模型没有输出)
					pricing.inputPricePer1k(), // unitPriceIn
					BigDecimal.ZERO, // unitPriceOut
					pricing.currency() // currency
			);

			BigDecimal cost = costLedgerService.recordCost(entry);
			log.info("Tracked embedding cost for session {}: {} {}", sessionId, cost, pricing.currency());

		}
		catch (Exception e) {
			log.error("Failed to track embedding cost for session {}: {}", sessionId, e.getMessage(), e);
		}
	}

	/**
	 * 清理会话映射
	 */
	public void unregisterSession(String sessionId) {
		if (sessionId != null) {
			sessionAgentMap.remove(sessionId);
		}
	}

	/**
	 * Usage 元数据，用于返回给前端
	 */
	public record UsageMetadata(String provider, String model, long inputTokens, long outputTokens) {

		public String toJson() {
			try {
				Map<String, Object> map = new HashMap<>();
				map.put("provider", provider);
				map.put("model", model);
				Map<String, Object> usage = new HashMap<>();
				usage.put("promptTokens", inputTokens);
				usage.put("completionTokens", outputTokens);
				map.put("usage", usage);
				return new ObjectMapper().writeValueAsString(map);
			}
			catch (Exception e) {
				return "{}";
			}
		}

	}

}
