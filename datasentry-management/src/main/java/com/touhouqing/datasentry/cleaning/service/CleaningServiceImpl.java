package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.dto.CleaningCheckRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningResponse;
import com.touhouqing.datasentry.cleaning.enums.CleaningBindingType;
import com.touhouqing.datasentry.cleaning.enums.CleaningCostChannel;
import com.touhouqing.datasentry.cleaning.mapper.CleaningAllowlistMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningBindingMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningAllowlist;
import com.touhouqing.datasentry.cleaning.model.CleaningBinding;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningJob;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicySnapshot;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.pipeline.CleaningPipeline;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CleaningServiceImpl implements CleaningService {

	private final CleaningPolicyResolver policyResolver;

	private final CleaningBindingMapper bindingMapper;

	private final CleaningAllowlistMapper allowlistMapper;

	private final CleaningJobMapper jobMapper;

	private final CleaningPolicyMapper policyMapper;

	private final CleaningPipeline pipeline;

	private final CleaningTokenEstimator tokenEstimator;

	private final CleaningPricingService pricingService;

	private final CleaningCostLedgerService costLedgerService;

	private final CleaningShadowService shadowService;

	private final DataSentryProperties dataSentryProperties;

	@Override
	public CleaningResponse check(Long agentId, CleaningCheckRequest request, String traceId) {
		return execute(agentId, request, traceId, false);
	}

	@Override
	public CleaningResponse sanitize(Long agentId, CleaningCheckRequest request, String traceId) {
		return execute(agentId, request, traceId, true);
	}

	private CleaningResponse execute(Long agentId, CleaningCheckRequest request, String traceId,
			boolean sanitizeRequested) {
		CleaningPolicySnapshot snapshot = resolvePolicySnapshot(agentId, request, traceId);
		CleaningJob onlineJob = resolveOnlineBudgetJob(agentId);
		long estimatedTokens = tokenEstimator.estimateTokens(request.getText());
		boolean failClosed = isFailClosedTriggered(onlineJob, estimatedTokens);
		List<CleaningAllowlist> allowlists = allowlistMapper.findActive();
		if (allowlists == null) {
			allowlists = List.of();
		}
		CleaningContext context = CleaningContext.builder()
			.agentId(agentId)
			.traceId(traceId)
			.originalText(request.getText())
			.policySnapshot(snapshot)
			.build();
		context.getMetadata().put("scene", request.getScene());
		context.getMetadata().put("allowlists", allowlists);
		context.getMetadata().put("disableL3", failClosed);
		context.getMetadata().put("shadowEnabled", dataSentryProperties.getCleaning().getShadow().isEnabled());
		context.getMetrics().put("startTimeMs", System.currentTimeMillis());
		log.info(
				"Cleaning online start traceId={} agentId={} scene={} policyId={} policyName={} sanitizeRequested={} estimatedTokens={} failClosed={} allowlists={}",
				traceId, agentId, request.getScene(), snapshot.getPolicyId(), snapshot.getPolicyName(),
				sanitizeRequested, estimatedTokens, failClosed, allowlists.size());
		CleaningContext result = pipeline.execute(context, sanitizeRequested);
		shadowService.compareAndRecordIfEnabled(result, snapshot,
				() -> pipeline.execute(buildShadowContext(result, snapshot), sanitizeRequested));
		shadowService.submitIfEnabled(result, snapshot.getConfig());
		recordOnlineCost(agentId, traceId, estimatedTokens, failClosed);
		log.info("Cleaning online result traceId={} agentId={} verdict={} categories={} findings={}", traceId, agentId,
				result.getVerdict(), resolveCategories(result.getFindings()),
				result.getFindings() != null ? result.getFindings().size() : 0);
		return CleaningResponse.builder()
			.verdict(result.getVerdict() != null ? result.getVerdict().name() : null)
			.categories(resolveCategories(result.getFindings()))
			.sanitizedText(sanitizeRequested ? result.getSanitizedText() : null)
			.build();
	}

	private CleaningContext buildShadowContext(CleaningContext mainResult, CleaningPolicySnapshot snapshot) {
		CleaningContext shadowContext = CleaningContext.builder()
			.agentId(mainResult.getAgentId())
			.traceId(mainResult.getTraceId())
			.originalText(mainResult.getOriginalText())
			.policySnapshot(snapshot)
			.jobRunId(mainResult.getJobRunId())
			.datasourceId(mainResult.getDatasourceId())
			.tableName(mainResult.getTableName())
			.pkJson(mainResult.getPkJson())
			.columnName(mainResult.getColumnName())
			.build();
		for (Map.Entry<String, Object> entry : mainResult.getMetadata().entrySet()) {
			shadowContext.getMetadata().put(entry.getKey(), entry.getValue());
		}
		shadowContext.getMetadata().put("shadowTrack", true);
		shadowContext.getMetrics().put("startTimeMs", System.currentTimeMillis());
		return shadowContext;
	}

	private CleaningJob resolveOnlineBudgetJob(Long agentId) {
		return jobMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CleaningJob>()
			.eq(CleaningJob::getAgentId, agentId)
			.eq(CleaningJob::getEnabled, 1)
			.orderByDesc(CleaningJob::getId)
			.last("LIMIT 1"));
	}

	private boolean isFailClosedTriggered(CleaningJob onlineJob, long estimatedTokens) {
		int defaultLimit = dataSentryProperties.getCleaning().getBudget().getOnlineRequestTokenLimit();
		int tokenLimit = onlineJob != null && onlineJob.getOnlineRequestTokenLimit() != null
				? onlineJob.getOnlineRequestTokenLimit() : defaultLimit;
		boolean failClosedEnabled = onlineJob != null && onlineJob.getOnlineFailClosedEnabled() != null
				? onlineJob.getOnlineFailClosedEnabled() == 1
				: dataSentryProperties.getCleaning().getBudget().isFailClosedEnabled();
		return failClosedEnabled && tokenLimit > 0 && estimatedTokens > tokenLimit;
	}

	private void recordOnlineCost(Long agentId, String traceId, long estimatedTokens, boolean failClosed) {
		String detectorLevel = failClosed ? "L1" : "L3";
		CleaningPricingService.Pricing pricing = pricingService.resolvePricing(CleaningPricingService.DEFAULT_PROVIDER,
				CleaningPricingService.DEFAULT_MODEL);
		costLedgerService.recordCost(new CleaningCostLedgerService.CostEntry(null, null, agentId, traceId,
				CleaningCostChannel.ONLINE, detectorLevel, pricing.provider(), pricing.model(), estimatedTokens, 0L,
				pricing.inputPricePer1k(), pricing.outputPricePer1k(), pricing.currency()));
	}

	private CleaningPolicySnapshot resolvePolicySnapshot(Long agentId, CleaningCheckRequest request, String traceId) {
		Long policyId = request.getPolicyId();
		if (policyId == null) {
			CleaningBinding binding = resolveBinding(agentId, request.getScene());
			if (binding != null && binding.getPolicyId() != null) {
				policyId = binding.getPolicyId();
			}
		}
		if (policyId == null) {
			policyId = resolveFallbackPolicyId();
		}
		if (policyId == null) {
			throw new InvalidInputException("未找到可用的清理策略：请在请求中传 policyId，或为当前 Agent 配置 ONLINE_TEXT 默认绑定");
		}
		return policyResolver.resolveSnapshot(policyId, traceId);
	}

	private CleaningBinding resolveBinding(Long agentId, String scene) {
		CleaningBinding binding = null;
		if (scene != null && !scene.isBlank()) {
			binding = bindingMapper.findByAgentAndScene(agentId, CleaningBindingType.ONLINE_TEXT.name(), scene);
		}
		if (binding == null) {
			binding = bindingMapper.findDefaultByAgent(agentId, CleaningBindingType.ONLINE_TEXT.name());
		}
		return binding;
	}

	private Long resolveFallbackPolicyId() {
		CleaningPolicy fallback = policyMapper
			.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CleaningPolicy>()
				.eq(CleaningPolicy::getEnabled, 1)
				.orderByDesc(CleaningPolicy::getId)
				.last("LIMIT 1"));
		if (fallback == null) {
			return null;
		}
		return fallback.getId();
	}

	private List<String> resolveCategories(List<Finding> findings) {
		if (findings == null || findings.isEmpty()) {
			return List.of();
		}
		Set<String> categories = new LinkedHashSet<>();
		for (Finding finding : findings) {
			if (finding.getCategory() != null) {
				categories.add(finding.getCategory());
			}
		}
		return categories.stream().collect(Collectors.toList());
	}

}
