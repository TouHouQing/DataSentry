package com.touhouqing.datasentry.cleaning.pipeline;

import com.touhouqing.datasentry.cleaning.detector.LlmDetector;
import com.touhouqing.datasentry.cleaning.detector.L2Detector;
import com.touhouqing.datasentry.cleaning.detector.RegexDetector;
import com.touhouqing.datasentry.cleaning.enums.CleaningRuleType;
import com.touhouqing.datasentry.cleaning.model.CleaningAllowlist;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicySnapshot;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.model.NodeResult;
import com.touhouqing.datasentry.cleaning.util.CleaningAllowlistMatcher;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class DetectNode implements PipelineNode {

	private final RegexDetector regexDetector;

	private final L2Detector l2Detector;

	private final LlmDetector llmDetector;

	@Override
	public NodeResult process(CleaningContext context) {
		String text = context.getNormalizedText() != null ? context.getNormalizedText() : context.getOriginalText();
		CleaningPolicySnapshot snapshot = context.getPolicySnapshot();
		CleaningPolicyConfig policyConfig = snapshot != null && snapshot.getConfig() != null ? snapshot.getConfig()
				: new CleaningPolicyConfig();
		boolean disableL3 = isDisableL3(context);
		List<CleaningRule> rules = snapshot != null ? snapshot.getRules() : List.of();
		List<Finding> l1Findings = new ArrayList<>();
		List<Finding> l2Findings = new ArrayList<>();
		List<Finding> l3Findings = new ArrayList<>();
		List<Finding> findings = new ArrayList<>();
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
		boolean escalatedToL3 = !disableL3 && snapshot != null && snapshot.getConfig() != null
				&& snapshot.getConfig().resolvedLlmEnabled()
				&& shouldEscalateToL3(l1Findings, l2Findings, policyConfig);
		if (escalatedToL3) {
			List<CleaningRule> llmRules = rules.stream()
					.filter(rule -> CleaningRuleType.LLM.name().equalsIgnoreCase(rule.getRuleType()))
					.toList();

			for (CleaningRule rule : llmRules) {
				String prompt = null;
				try {
					if (rule.getConfigJson() != null && !rule.getConfigJson().isBlank()) {
						Map<String, Object> config = JsonUtil.getObjectMapper()
							.readValue(rule.getConfigJson(), Map.class);
						if (config.get("prompt") instanceof String) {
							prompt = (String) config.get("prompt");
						}
					}
				}
				catch (Exception e) {
					log.warn("Failed to parse LLM rule config: {}", rule.getId(), e);
				}
				l3Findings.addAll(llmDetector.detect(text, prompt));
			}
			findings.addAll(l3Findings);
		}
		List<CleaningAllowlist> allowlists = getAllowlists(context);
		List<Finding> filteredFindings = CleaningAllowlistMatcher.filterFindings(text, findings, allowlists);
		context.setFindings(filteredFindings);
		log.info(
				"Cleaning detect runId={} column={} rules={} l1={} l2={} l3={} total={} filtered={} allowlists={} escalatedToL3={} disableL3={}",
				context.getJobRunId(), context.getColumnName(), rules.size(), l1Findings.size(), l2Findings.size(),
				l3Findings.size(), findings.size(), filteredFindings.size(), allowlists.size(), escalatedToL3,
				disableL3);
		return NodeResult.ok();
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

	@SuppressWarnings("unchecked")
	private List<CleaningAllowlist> getAllowlists(CleaningContext context) {
		Object value = context.getMetadata().get("allowlists");
		if (value instanceof List) {
			return (List<CleaningAllowlist>) value;
		}
		return List.of();
	}

}
