package com.touhouqing.datasentry.cleaning.pipeline;

import com.touhouqing.datasentry.cleaning.context.AiCostContextHolder;
import com.touhouqing.datasentry.cleaning.detector.L2Detector;
import com.touhouqing.datasentry.cleaning.detector.LlmDetector;
import com.touhouqing.datasentry.cleaning.detector.RegexDetector;
import com.touhouqing.datasentry.cleaning.enums.CleaningRuleType;
import com.touhouqing.datasentry.cleaning.model.CleaningAllowlist;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicySnapshot;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.model.NodeResult;
import com.touhouqing.datasentry.cleaning.util.CleaningOutboundSanitizer;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.cleaning.util.CleaningAllowlistMatcher;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@Slf4j
@RequiredArgsConstructor
public class DetectNode implements PipelineNode {

	private final RegexDetector regexDetector;

	private final L2Detector l2Detector;

	private final LlmDetector llmDetector;

	private final DataSentryProperties dataSentryProperties;

	@Override
	public NodeResult process(CleaningContext context) {
		String text = context.getNormalizedText() != null ? context.getNormalizedText() : context.getOriginalText();
		CleaningPolicySnapshot snapshot = context.getPolicySnapshot();
		CleaningPolicyConfig policyConfig = snapshot != null && snapshot.getConfig() != null ? snapshot.getConfig()
				: new CleaningPolicyConfig();
		boolean disableL3 = isDisableL3(context);
		List<CleaningRule> rules = snapshot != null ? snapshot.getRules() : List.of();
		List<CleaningRule> llmRules = rules.stream()
			.filter(rule -> CleaningRuleType.LLM.name().equalsIgnoreCase(rule.getRuleType()))
			.toList();
		boolean hasLlmRules = !llmRules.isEmpty();
		boolean l3Enabled = policyConfig.resolvedLlmEnabled();
		List<Finding> l1Findings = new ArrayList<>();
		List<Finding> l2Findings = new ArrayList<>();
		List<Finding> l3Findings = new ArrayList<>();
		List<Finding> findings = new ArrayList<>();
		int llmParseSuccessCount = 0;
		int llmParseFailCount = 0;
		int l3EmptyStructuredCount = 0;
		Map<String, Integer> l3ModeCounts = new HashMap<>();
		for (CleaningRule rule : rules) {
			if (rule.getRuleType() == null) {
				continue;
			}
			if (CleaningRuleType.REGEX.name().equalsIgnoreCase(rule.getRuleType())) {
				l1Findings.addAll(regexDetector.detect(text, rule));
			}
			if (CleaningRuleType.L2_DUMMY.name().equalsIgnoreCase(rule.getRuleType())) {
				l2Findings.addAll(l2Detector.detect(text, rule, policyConfig));
			}
		}
		findings.addAll(l1Findings);
		findings.addAll(l2Findings);
		boolean legacyEscalatedToL3 = shouldEscalateToL3(l1Findings, l2Findings, policyConfig);
		boolean runL3 = !disableL3 && l3Enabled && hasLlmRules;
		context.getMetadata().put("l3Attempted", runL3);
		if (runL3) {
			String l3Text = resolveL3OutboundText(text, policyConfig, context);
			List<L3RuleResult> ruleResults = resolvePrecomputedOrDetect(context, l3Text, llmRules);
			for (L3RuleResult ruleResult : ruleResults) {
				LlmDetector.LlmDetectResult llmResult = ruleResult.result();
				String mode = llmResult.mode() != null ? llmResult.mode() : "UNKNOWN";
				l3ModeCounts.merge(mode, 1, Integer::sum);
				int findingCount = llmResult.findings() != null ? llmResult.findings().size() : 0;
				if (llmResult.parseSuccess()) {
					llmParseSuccessCount++;
					if (findingCount == 0) {
						l3EmptyStructuredCount++;
					}
					l3Findings.addAll(llmResult.findings());
				}
				else {
					llmParseFailCount++;
				}
				log.info(
						"event=L3_RULE_RESULT runId={} column={} ruleId={} parseSuccess={} mode={} findings={} errorCode={}",
						context.getJobRunId(), context.getColumnName(), ruleResult.ruleId(), llmResult.parseSuccess(),
						mode, findingCount, llmResult.errorCode());
			}
			findings.addAll(l3Findings);
		}
		boolean l3AllParseFailed = runL3 && llmParseSuccessCount == 0;
		context.getMetadata().put("l3AllParseFailed", l3AllParseFailed);
		context.getMetrics().put("l3RuleCount", llmRules.size());
		context.getMetrics().put("l3ParseSuccessCount", llmParseSuccessCount);
		context.getMetrics().put("l3ParseFailCount", llmParseFailCount);
		context.getMetrics().put("l3EmptyStructuredCount", l3EmptyStructuredCount);
		context.getMetrics().put("l3ModeCounts", l3ModeCounts);
		List<CleaningAllowlist> allowlists = getAllowlists(context);
		List<Finding> filteredFindings = CleaningAllowlistMatcher.filterFindings(text, findings, allowlists);
		context.setFindings(filteredFindings);
		log.info(
				"Cleaning detect runId={} column={} rules={} l1={} l2={} l3={} total={} filtered={} allowlists={} hasLlmRules={} l3Enabled={} runL3={} l3ParseSuccess={} l3ParseFail={} l3EmptyStructured={} l3Modes={} l3AllParseFailed={} legacyEscalatedToL3={} disableL3={}",
				context.getJobRunId(), context.getColumnName(), rules.size(), l1Findings.size(), l2Findings.size(),
				l3Findings.size(), findings.size(), filteredFindings.size(), allowlists.size(), hasLlmRules, l3Enabled,
				runL3, llmParseSuccessCount, llmParseFailCount, l3EmptyStructuredCount, l3ModeCounts, l3AllParseFailed,
				legacyEscalatedToL3, disableL3);
		return NodeResult.ok();
	}

	@SuppressWarnings("unchecked")
	private List<L3RuleResult> resolvePrecomputedOrDetect(CleaningContext context, String text,
			List<CleaningRule> llmRules) {
		Object precomputedObject = context.getMetadata().get("precomputedL3Results");
		if (!(precomputedObject instanceof Map<?, ?> precomputedMap) || llmRules == null || llmRules.isEmpty()) {
			return detectL3RuleResults(context, text, llmRules);
		}
		List<L3RuleResult> results = new ArrayList<>();
		for (CleaningRule rule : llmRules) {
			Long ruleId = rule != null ? rule.getId() : null;
			if (ruleId == null) {
				continue;
			}
			Object ruleResultObject = precomputedMap.get(ruleId);
			if (!(ruleResultObject instanceof LlmDetector.LlmDetectResult llmResult)) {
				results.add(new L3RuleResult(ruleId,
						LlmDetector.LlmDetectResult.failure("L3_PRECOMPUTED_MISSING", null, "L3_PRECOMPUTED_MISSING")));
				continue;
			}
			results.add(new L3RuleResult(ruleId, llmResult));
		}
		if (!results.isEmpty()) {
			return results;
		}
		return detectL3RuleResults(context, text, llmRules);
	}

	private boolean shouldEscalateToL3(List<Finding> l1Findings, List<Finding> l2Findings,
			CleaningPolicyConfig config) {
		if (!l1Findings.isEmpty()) {
			return true;
		}
		if (l2Findings.isEmpty()) {
			return false;
		}
		double reviewThreshold = config != null ? config.resolvedReviewThreshold() : 0.4;
		for (Finding finding : l2Findings) {
			double severity = finding.getSeverity() != null ? finding.getSeverity() : 0.0;
			if (severity >= reviewThreshold) {
				return true;
			}
		}
		return false;
	}

	private boolean isDisableL3(CleaningContext context) {
		Object value = context.getMetadata().get("disableL3");
		return value instanceof Boolean && (Boolean) value;
	}

	private String resolveL3OutboundText(String text, CleaningPolicyConfig config, CleaningContext context) {
		if (text == null || text.isBlank() || config == null || !config.resolvedOutboundSanitizeEnabled()) {
			return text;
		}
		String mode = config.resolvedOutboundSanitizeMode();
		String outboundText = CleaningOutboundSanitizer.sanitize(text, mode);
		if (!text.equals(outboundText) && context != null) {
			context.getMetadata().put("outboundSanitized", true);
			context.getMetadata().put("outboundSanitizeMode", mode);
		}
		return outboundText;
	}

	@SuppressWarnings("unchecked")
	private List<CleaningAllowlist> getAllowlists(CleaningContext context) {
		Object value = context.getMetadata().get("allowlists");
		if (value instanceof List) {
			return (List<CleaningAllowlist>) value;
		}
		return List.of();
	}

	private List<L3RuleResult> detectL3RuleResults(CleaningContext context, String text, List<CleaningRule> llmRules) {
		if (llmRules == null || llmRules.isEmpty()) {
			return List.of();
		}
		int configuredConcurrency = 1;
		if (dataSentryProperties != null && dataSentryProperties.getCleaning() != null
				&& dataSentryProperties.getCleaning().getL3() != null) {
			configuredConcurrency = dataSentryProperties.getCleaning().getL3().getMaxRuleConcurrency();
		}
		int concurrency = Math.max(1, Math.min(configuredConcurrency, llmRules.size()));
		if (concurrency == 1) {
			List<L3RuleResult> serialResults = new ArrayList<>();
			for (CleaningRule rule : llmRules) {
				serialResults.add(callSingleRule(context, text, rule));
			}
			return serialResults;
		}
		ExecutorService executor = Executors.newFixedThreadPool(concurrency);
		try {
			List<Future<L3RuleResult>> futures = new ArrayList<>();
			for (CleaningRule rule : llmRules) {
				Callable<L3RuleResult> callable = () -> callSingleRule(context, text, rule);
				futures.add(executor.submit(callable));
			}
			List<L3RuleResult> results = new ArrayList<>();
			for (Future<L3RuleResult> future : futures) {
				try {
					results.add(future.get());
				}
				catch (ExecutionException e) {
					Throwable cause = e.getCause() != null ? e.getCause() : e;
					log.warn("Failed to execute L3 rule", cause);
					results.add(new L3RuleResult(null, LlmDetector.LlmDetectResult.failure("L3_RULE_EXECUTION_FAILED",
							null, "L3_RULE_EXECUTION_FAILED")));
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					results.add(new L3RuleResult(null,
							LlmDetector.LlmDetectResult.failure("L3_RULE_INTERRUPTED", null, "L3_RULE_INTERRUPTED")));
				}
			}
			return results;
		}
		finally {
			executor.shutdownNow();
		}
	}

	private L3RuleResult callSingleRule(CleaningContext context, String text, CleaningRule rule) {
		String prompt = resolvePrompt(rule);
		boolean contextBound = false;
		try {
			String traceId = resolveTraceId(context);
			Long agentId = context != null ? context.getAgentId() : null;
			if (traceId != null && agentId != null) {
				AiCostContextHolder.setContext(traceId, agentId);
				contextBound = true;
			}
			LlmDetector.LlmDetectResult llmResult = llmDetector.detectStructured(text, prompt);
			return new L3RuleResult(rule != null ? rule.getId() : null, llmResult);
		}
		finally {
			if (contextBound) {
				AiCostContextHolder.clearContext();
			}
		}
	}

	private String resolveTraceId(CleaningContext context) {
		if (context == null) {
			return null;
		}
		String traceId = context.getTraceId();
		if (traceId != null && !traceId.isBlank()) {
			return traceId;
		}
		Long runId = context.getJobRunId();
		if (runId != null) {
			return String.valueOf(runId);
		}
		return null;
	}

	private String resolvePrompt(CleaningRule rule) {
		if (rule == null || rule.getConfigJson() == null || rule.getConfigJson().isBlank()) {
			return null;
		}
		try {
			Map<String, Object> config = JsonUtil.getObjectMapper().readValue(rule.getConfigJson(), Map.class);
			if (config.get("prompt") instanceof String configuredPrompt) {
				return configuredPrompt;
			}
		}
		catch (Exception e) {
			log.warn("Failed to parse LLM rule config: {}", rule.getId(), e);
		}
		return null;
	}

	private record L3RuleResult(Long ruleId, LlmDetector.LlmDetectResult result) {
	}

}
