package com.touhouqing.datasentry.cleaning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.touhouqing.datasentry.cleaning.enums.CleaningCostChannel;
import com.touhouqing.datasentry.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 智能数据分析对话成本追踪器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisCostTracker {

	private final CleaningCostLedgerService costLedgerService;

	private final CleaningPricingService pricingService;

	private final ObjectMapper objectMapper;

	/**
	 * 记录对话消息的成本
	 * @param message 对话消息
	 * @param agentId 智能体ID
	 */
	public void trackMessageCost(ChatMessage message, Long agentId) {
		if (message == null || !"assistant".equals(message.getRole())) {
			// 仅记录 AI 回复的成本
			return;
		}

		try {
			// 从 metadata 中提取 token 使用量和模型信息
			UsageInfo usage = extractUsageInfo(message.getMetadata());
			if (usage == null || usage.provider() == null || usage.model() == null) {
				log.debug("No usage info found for message {}", message.getId());
				return;
			}

			// 获取价格
			CleaningPricingService.Pricing pricing = pricingService.resolvePricing(usage.provider(),
					usage.model());

			// 记录成本
			CleaningCostLedgerService.CostEntry entry = new CleaningCostLedgerService.CostEntry(null, // jobId
					null, // jobRunId
					agentId, // agentId
					message.getSessionId(), // traceId (使用 sessionId 作为追踪ID)
					CleaningCostChannel.ANALYSIS, // channel
					null, // detectorLevel
					usage.provider(), // provider
					usage.model(), // model
					usage.inputTokens(), // inputTokensEst
					usage.outputTokens(), // outputTokensEst
					pricing.inputPricePer1k(), // unitPriceIn
					pricing.outputPricePer1k(), // unitPriceOut
					pricing.currency() // currency
			);

			BigDecimal cost = costLedgerService.recordCost(entry);
			log.info("Recorded analysis cost for session {}: {} {}", message.getSessionId(), cost,
					pricing.currency());

		}
		catch (Exception e) {
			log.error("Failed to track cost for message {}: {}", message.getId(), e.getMessage(), e);
		}
	}

	/**
	 * 从 metadata JSON 中提取使用信息
	 */
	private UsageInfo extractUsageInfo(String metadata) {
		if (metadata == null || metadata.isBlank()) {
			return null;
		}

		try {
			JsonNode node = objectMapper.readTree(metadata);

			// 提取 provider 和 model
			String provider = node.has("provider") ? node.get("provider").asText() : null;
			String model = node.has("model") ? node.get("model").asText() : null;

			// 提取 token 使用量
			long inputTokens = 0;
			long outputTokens = 0;

			if (node.has("usage")) {
				JsonNode usage = node.get("usage");
				inputTokens = usage.has("promptTokens") ? usage.get("promptTokens").asLong() : 0;
				outputTokens = usage.has("completionTokens") ? usage.get("completionTokens").asLong() : 0;
			}

			if (provider == null || model == null) {
				return null;
			}

			return new UsageInfo(provider, model, inputTokens, outputTokens);

		}
		catch (Exception e) {
			log.error("Failed to parse metadata: {}", e.getMessage());
			return null;
		}
	}

	private record UsageInfo(String provider, String model, long inputTokens, long outputTokens) {
	}

}
