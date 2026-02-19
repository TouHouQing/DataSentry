package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyCopilotSuggestionView;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyRuleMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewFeedbackRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRuleMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyRule;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewFeedbackRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CleaningPolicyCopilotService {

	private static final int DEFAULT_SAMPLE_LIMIT = 200;

	private static final int MAX_SAMPLE_LIMIT = 1000;

	private final CleaningPolicyMapper policyMapper;

	private final CleaningPolicyRuleMapper policyRuleMapper;

	private final CleaningRuleMapper ruleMapper;

	private final CleaningReviewFeedbackRecordMapper reviewFeedbackRecordMapper;

	public CleaningPolicyCopilotSuggestionView suggest(Long policyId, Long jobRunId, Long agentId, Integer limit) {
		CleaningPolicy policy = policyMapper.selectById(policyId);
		if (policy == null) {
			throw new InvalidInputException("策略不存在");
		}
		int safeLimit = resolveLimit(limit);
		List<CleaningReviewFeedbackRecord> samples = reviewFeedbackRecordMapper.listLatest(jobRunId, agentId,
				safeLimit);

		Map<String, Object> currentConfig = parseConfig(policy.getConfigJson());
		double blockThreshold = normalized01(asDouble(currentConfig.get("blockThreshold"), 0.7D));
		double reviewThreshold = normalized01(asDouble(currentConfig.get("reviewThreshold"), 0.4D));
		double l2Threshold = normalized01(asDouble(currentConfig.get("l2Threshold"), 0.6D));
		boolean llmEnabled = asBoolean(currentConfig.get("llmEnabled"), true);
		boolean shadowEnabled = asBoolean(currentConfig.get("shadowEnabled"), false);
		double shadowSampleRatio = normalized01(asDouble(currentConfig.get("shadowSampleRatio"), 0D));

		Map<String, CategoryCounter> categoryCounter = new LinkedHashMap<>();
		int disputedTotal = 0;
		for (CleaningReviewFeedbackRecord sample : samples) {
			boolean disputed = "REJECTED".equals(sample.getFinalStatus()) || "CONFLICT".equals(sample.getFinalStatus());
			if (disputed) {
				disputedTotal++;
			}
			Set<String> categories = parseCategories(sample.getCategoriesJson());
			if (categories.isEmpty()) {
				categories = Set.of("UNCATEGORIZED");
			}
			for (String category : categories) {
				categoryCounter.computeIfAbsent(category, key -> new CategoryCounter()).add(disputed);
			}
		}
		double disputeRate = samples.isEmpty() ? 0D : (disputedTotal * 1.0D / samples.size());

		String recommendationLevel;
		String recommendedDefaultAction;
		List<String> insights = new ArrayList<>();
		if (samples.isEmpty()) {
			recommendationLevel = "BOOTSTRAP";
			recommendedDefaultAction = "DETECT_ONLY";
			shadowEnabled = true;
			shadowSampleRatio = Math.max(shadowSampleRatio, 0.1D);
			insights.add("反馈样本不足，建议先以保守策略运行并收集审核反馈。");
		}
		else if (disputeRate >= 0.35D) {
			recommendationLevel = "CONSERVATIVE";
			recommendedDefaultAction = "REVIEW_THEN_WRITEBACK";
			blockThreshold = normalized01(blockThreshold + 0.08D);
			reviewThreshold = normalized01(reviewThreshold + 0.10D);
			llmEnabled = true;
			shadowEnabled = true;
			shadowSampleRatio = Math.max(shadowSampleRatio, 0.25D);
			insights.add("争议率较高，建议提高阈值并提升人审占比，降低误杀风险。");
		}
		else if (disputeRate <= 0.10D && samples.size() >= 30) {
			recommendationLevel = "AUTOMATED";
			recommendedDefaultAction = "SANITIZE_WRITEBACK";
			blockThreshold = normalized01(blockThreshold - 0.05D);
			reviewThreshold = normalized01(reviewThreshold - 0.05D);
			shadowEnabled = true;
			shadowSampleRatio = Math.max(shadowSampleRatio, 0.10D);
			insights.add("争议率较低，可提升自动化写回比例以降低审核负载。");
		}
		else {
			recommendationLevel = "BALANCED";
			recommendedDefaultAction = policy.getDefaultAction() != null ? policy.getDefaultAction() : "DETECT_ONLY";
			shadowEnabled = true;
			shadowSampleRatio = Math.max(shadowSampleRatio, 0.15D);
			insights.add("争议率处于中间区间，建议通过 Shadow 持续观察并小步调优。");
		}

		reviewThreshold = normalizedReviewThreshold(reviewThreshold, blockThreshold);
		l2Threshold = normalized01(l2Threshold);

		List<Long> recommendedRuleIds = recommendedRuleIds(policyId, categoryCounter, insights);
		Map<String, Object> recommendedConfig = new LinkedHashMap<>();
		recommendedConfig.put("blockThreshold", blockThreshold);
		recommendedConfig.put("reviewThreshold", reviewThreshold);
		recommendedConfig.put("l2Threshold", l2Threshold);
		recommendedConfig.put("llmEnabled", llmEnabled);
		recommendedConfig.put("shadowEnabled", shadowEnabled);
		recommendedConfig.put("shadowSampleRatio", shadowSampleRatio);

		return CleaningPolicyCopilotSuggestionView.builder()
			.policyId(policy.getId())
			.policyName(policy.getName())
			.sampleSize(samples.size())
			.disputeRate(disputeRate)
			.recommendationLevel(recommendationLevel)
			.recommendedDefaultAction(recommendedDefaultAction)
			.recommendedConfig(recommendedConfig)
			.recommendedRuleIds(recommendedRuleIds)
			.insights(insights)
			.generatedTime(LocalDateTime.now())
			.build();
	}

	private List<Long> recommendedRuleIds(Long policyId, Map<String, CategoryCounter> categoryCounter,
			List<String> insights) {
		List<Long> currentRuleIds = policyRuleMapper
			.selectList(new LambdaQueryWrapper<CleaningPolicyRule>().eq(CleaningPolicyRule::getPolicyId, policyId))
			.stream()
			.map(CleaningPolicyRule::getRuleId)
			.collect(Collectors.toCollection(LinkedHashSet::new))
			.stream()
			.toList();
		Set<Long> result = new LinkedHashSet<>(currentRuleIds);
		List<String> riskyCategories = categoryCounter.entrySet()
			.stream()
			.filter(entry -> entry.getValue().total >= 3 && entry.getValue().disputeRate() >= 0.4D)
			.sorted(Comparator
				.comparingDouble((Map.Entry<String, CategoryCounter> entry) -> entry.getValue().disputeRate())
				.reversed())
			.limit(3)
			.map(Map.Entry::getKey)
			.toList();
		if (!riskyCategories.isEmpty()) {
			insights.add("高争议分类：" + String.join("、", riskyCategories));
			List<Long> riskyRuleIds = ruleMapper
				.selectList(new LambdaQueryWrapper<CleaningRule>().eq(CleaningRule::getEnabled, 1)
					.in(CleaningRule::getCategory, riskyCategories))
				.stream()
				.map(CleaningRule::getId)
				.toList();
			result.addAll(riskyRuleIds);
		}
		else {
			insights.add("未发现显著高争议分类，可继续按当前规则集迭代。");
		}
		return result.stream().limit(30).toList();
	}

	private Set<String> parseCategories(String categoriesJson) {
		if (categoriesJson == null || categoriesJson.isBlank()) {
			return Set.of();
		}
		try {
			List<?> raw = JsonUtil.getObjectMapper().readValue(categoriesJson, List.class);
			Set<String> categories = new LinkedHashSet<>();
			for (Object item : raw) {
				if (item == null) {
					continue;
				}
				String value = String.valueOf(item).trim();
				if (!value.isBlank()) {
					categories.add(value);
				}
			}
			return categories;
		}
		catch (Exception e) {
			return Set.of();
		}
	}

	private Map<String, Object> parseConfig(String configJson) {
		if (configJson == null || configJson.isBlank()) {
			return new LinkedHashMap<>();
		}
		try {
			Map<String, Object> parsed = JsonUtil.getObjectMapper().readValue(configJson, Map.class);
			return parsed != null ? parsed : new LinkedHashMap<>();
		}
		catch (Exception e) {
			return new LinkedHashMap<>();
		}
	}

	private int resolveLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_SAMPLE_LIMIT;
		}
		return Math.min(limit, MAX_SAMPLE_LIMIT);
	}

	private double asDouble(Object value, double fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return Double.parseDouble(String.valueOf(value));
		}
		catch (Exception e) {
			return fallback;
		}
	}

	private boolean asBoolean(Object value, boolean fallback) {
		if (value == null) {
			return fallback;
		}
		if (value instanceof Boolean bool) {
			return bool;
		}
		String text = String.valueOf(value).trim();
		if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
			return true;
		}
		if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
			return false;
		}
		return fallback;
	}

	private double normalized01(double value) {
		return Math.max(0D, Math.min(1D, value));
	}

	private double normalizedReviewThreshold(double reviewThreshold, double blockThreshold) {
		double normalizedBlock = normalized01(blockThreshold);
		double normalizedReview = normalized01(reviewThreshold);
		if (normalizedReview >= normalizedBlock) {
			return Math.max(0D, normalizedBlock - 0.05D);
		}
		return normalizedReview;
	}

	private static class CategoryCounter {

		private int total;

		private int disputed;

		private void add(boolean disputedHit) {
			total++;
			if (disputedHit) {
				disputed++;
			}
		}

		private double disputeRate() {
			if (total <= 0) {
				return 0D;
			}
			return disputed * 1.0D / total;
		}

	}

}
