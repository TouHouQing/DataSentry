package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.enums.CleaningCostChannel;
import com.touhouqing.datasentry.cleaning.mapper.CleaningCostLedgerMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningCostLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CleaningCostLedgerService {

	private final CleaningCostLedgerMapper costLedgerMapper;

	private final com.touhouqing.datasentry.mapper.AgentMapper agentMapper;

	public BigDecimal recordCost(CostEntry entry) {
		long inputTokens = Math.max(entry.inputTokensEst(), 0L);
		long outputTokens = Math.max(entry.outputTokensEst(), 0L);
		BigDecimal inCost = entry.unitPriceIn()
			.multiply(BigDecimal.valueOf(inputTokens))
			.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
		BigDecimal outCost = entry.unitPriceOut()
			.multiply(BigDecimal.valueOf(outputTokens))
			.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
		BigDecimal total = inCost.add(outCost).setScale(6, RoundingMode.HALF_UP);

		CleaningCostLedger ledger = CleaningCostLedger.builder()
			.jobId(entry.jobId())
			.jobRunId(entry.jobRunId())
			.agentId(entry.agentId())
			.traceId(entry.traceId())
			.channel(entry.channel().name())
			.detectorLevel(entry.detectorLevel())
			.provider(entry.provider())
			.model(entry.model())
			.inputTokensEst(inputTokens)
			.outputTokensEst(outputTokens)
			.unitPriceIn(entry.unitPriceIn())
			.unitPriceOut(entry.unitPriceOut())
			.costAmount(total)
			.currency(entry.currency())
			.createdTime(LocalDateTime.now())
			.build();
		costLedgerMapper.insert(ledger);
		return total;
	}

	public List<CleaningCostLedger> list(Long jobRunId, String traceId, String channel) {
		LambdaQueryWrapper<CleaningCostLedger> wrapper = new LambdaQueryWrapper<>();
		if (jobRunId != null) {
			wrapper.eq(CleaningCostLedger::getJobRunId, jobRunId);
		}
		if (traceId != null && !traceId.isBlank()) {
			wrapper.eq(CleaningCostLedger::getTraceId, traceId);
		}
		if (channel != null && !channel.isBlank()) {
			wrapper.eq(CleaningCostLedger::getChannel, channel.toUpperCase());
		}
		List<CleaningCostLedger> ledgers = costLedgerMapper.selectList(wrapper.orderByDesc(CleaningCostLedger::getId));

		if (!ledgers.isEmpty()) {
			// 批量获取 Agent Name
			Set<Long> agentIds = ledgers.stream()
				.map(CleaningCostLedger::getAgentId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

			// LOGGING: Check if we found any agent IDs
			// System.out.println("DEBUG: Found " + ledgers.size() + " ledgers. AgentIDs:
			// " + agentIds);

			if (!agentIds.isEmpty()) {
				Map<Long, String> agentNameMap = new HashMap<>();
				for (Long agentId : agentIds) {
					try {
						var agent = agentMapper.findById(agentId);
						if (agent != null) {
							agentNameMap.put(agentId, agent.getName());
						}
					}
					catch (Exception e) {
						// ignore
					}
				}

				for (CleaningCostLedger ledger : ledgers) {
					if (ledger.getAgentId() != null) {
						String name = agentNameMap.get(ledger.getAgentId());
						if (name != null) {
							ledger.setAgentName(name);
						}
						else {
							// If name not found, use ID as fallback so it's not empty
							ledger.setAgentName("Agent-" + ledger.getAgentId());
						}
					}
					else {
						ledger.setAgentName("Unknown (No ID)");
					}
				}
			}
		}

		return ledgers;
	}

	/**
	 * 查询会话的总成本
	 */
	public BigDecimal getSessionTotalCost(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return BigDecimal.ZERO;
		}

		LambdaQueryWrapper<CleaningCostLedger> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(CleaningCostLedger::getTraceId, sessionId)
			.eq(CleaningCostLedger::getChannel, CleaningCostChannel.ANALYSIS.name());

		List<CleaningCostLedger> ledgers = costLedgerMapper.selectList(wrapper);

		return ledgers.stream().map(CleaningCostLedger::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/**
	 * 查询会话的成本明细
	 */
	public List<CleaningCostLedger> getSessionCostDetails(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return List.of();
		}

		LambdaQueryWrapper<CleaningCostLedger> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(CleaningCostLedger::getTraceId, sessionId)
			.eq(CleaningCostLedger::getChannel, CleaningCostChannel.ANALYSIS.name())
			.orderByDesc(CleaningCostLedger::getId);

		return costLedgerMapper.selectList(wrapper);
	}

	public record CostEntry(Long jobId, Long jobRunId, Long agentId, String traceId, CleaningCostChannel channel,
			String detectorLevel, String provider, String model, long inputTokensEst, long outputTokensEst,
			BigDecimal unitPriceIn, BigDecimal unitPriceOut, String currency) {
	}

}
