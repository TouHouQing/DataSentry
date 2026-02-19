package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.dto.CleaningReviewOptimizationView;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewFeedbackRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewFeedbackRecord;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CleaningReviewOptimizationService {

	private static final int DEFAULT_SAMPLE_LIMIT = 300;

	private static final int MAX_SAMPLE_LIMIT = 1000;

	private final CleaningReviewFeedbackRecordMapper reviewFeedbackRecordMapper;

	public CleaningReviewOptimizationView summarize(Long jobRunId, Long agentId, Integer limit) {
		int safeLimit = resolveLimit(limit);
		List<CleaningReviewFeedbackRecord> samples = reviewFeedbackRecordMapper.listLatest(jobRunId, agentId,
				safeLimit);
		if (samples.isEmpty()) {
			return CleaningReviewOptimizationView.builder()
				.totalSamples(0)
				.disputedRules(List.of())
				.thresholdSuggestions(List.of())
				.build();
		}
		Map<String, Counter> ruleCounter = new LinkedHashMap<>();
		Map<String, Counter> categoryCounter = new LinkedHashMap<>();
		for (CleaningReviewFeedbackRecord sample : samples) {
			Set<String> categories = resolveCategories(sample.getCategoriesJson());
			if (categories.isEmpty()) {
				categories = Set.of("UNCATEGORIZED");
			}
			boolean rejected = "REJECTED".equals(sample.getFinalStatus());
			boolean conflict = "CONFLICT".equals(sample.getFinalStatus());
			for (String category : categories) {
				String ruleKey = category + "|" + safeText(sample.getActionSuggested(), "NONE");
				ruleCounter.computeIfAbsent(ruleKey, key -> new Counter()).add(rejected, conflict);
				categoryCounter.computeIfAbsent(category, key -> new Counter()).add(rejected, conflict);
			}
		}
		List<CleaningReviewOptimizationView.DisputedRuleView> disputedRules = new ArrayList<>();
		for (Map.Entry<String, Counter> entry : ruleCounter.entrySet()) {
			String key = entry.getKey();
			Counter counter = entry.getValue();
			String[] splits = key.split("\\|", 2);
			BigDecimal disputeRate = counter.disputeRate();
			disputedRules.add(CleaningReviewOptimizationView.DisputedRuleView.builder()
				.category(splits[0])
				.actionSuggested(splits.length > 1 ? splits[1] : "NONE")
				.total(counter.total)
				.rejected(counter.rejected)
				.conflict(counter.conflict)
				.disputeRate(disputeRate)
				.build());
		}
		disputedRules.sort((left, right) -> compareByRateAndTotal(right.getDisputeRate(), right.getTotal(),
				left.getDisputeRate(), left.getTotal()));
		if (disputedRules.size() > 10) {
			disputedRules = disputedRules.subList(0, 10);
		}

		List<CleaningReviewOptimizationView.ThresholdSuggestionView> thresholdSuggestions = new ArrayList<>();
		for (Map.Entry<String, Counter> entry : categoryCounter.entrySet()) {
			String category = entry.getKey();
			Counter counter = entry.getValue();
			BigDecimal disputeRate = counter.disputeRate();
			if (disputeRate.compareTo(BigDecimal.valueOf(0.4)) >= 0) {
				thresholdSuggestions.add(CleaningReviewOptimizationView.ThresholdSuggestionView.builder()
					.category(category)
					.suggestion("降低 BLOCK 阈值并提升 REVIEW 比例")
					.reason("拒绝/冲突比例较高，疑似误杀偏多")
					.disputeRate(disputeRate)
					.build());
			}
			else if (disputeRate.compareTo(BigDecimal.valueOf(0.1)) <= 0 && counter.total >= 20) {
				thresholdSuggestions.add(CleaningReviewOptimizationView.ThresholdSuggestionView.builder()
					.category(category)
					.suggestion("提高 BLOCK 阈值以降低审核负载")
					.reason("争议比例较低，可尝试提升自动化处理比例")
					.disputeRate(disputeRate)
					.build());
			}
		}
		thresholdSuggestions.sort((left, right) -> right.getDisputeRate().compareTo(left.getDisputeRate()));
		if (thresholdSuggestions.size() > 5) {
			thresholdSuggestions = thresholdSuggestions.subList(0, 5);
		}
		return CleaningReviewOptimizationView.builder()
			.totalSamples(samples.size())
			.disputedRules(disputedRules)
			.thresholdSuggestions(thresholdSuggestions)
			.build();
	}

	private Set<String> resolveCategories(String categoriesJson) {
		if (categoriesJson == null || categoriesJson.isBlank()) {
			return Set.of();
		}
		try {
			List<?> raw = JsonUtil.getObjectMapper().readValue(categoriesJson, List.class);
			Set<String> categories = new LinkedHashSet<>();
			for (Object value : raw) {
				if (value == null) {
					continue;
				}
				String category = String.valueOf(value).trim();
				if (!category.isBlank()) {
					categories.add(category);
				}
			}
			return categories;
		}
		catch (Exception e) {
			return Set.of();
		}
	}

	private String safeText(String text, String fallback) {
		if (text == null || text.isBlank()) {
			return fallback;
		}
		return text.trim();
	}

	private int resolveLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_SAMPLE_LIMIT;
		}
		return Math.min(limit, MAX_SAMPLE_LIMIT);
	}

	private int compareByRateAndTotal(BigDecimal rateLeft, int totalLeft, BigDecimal rateRight, int totalRight) {
		int rateCompare = rateLeft.compareTo(rateRight);
		if (rateCompare != 0) {
			return rateCompare;
		}
		return Integer.compare(totalLeft, totalRight);
	}

	private static class Counter {

		private int total;

		private int rejected;

		private int conflict;

		private void add(boolean rejectedHit, boolean conflictHit) {
			total++;
			if (rejectedHit) {
				rejected++;
			}
			if (conflictHit) {
				conflict++;
			}
		}

		private BigDecimal disputeRate() {
			if (total <= 0) {
				return BigDecimal.ZERO;
			}
			return BigDecimal.valueOf(rejected + conflict).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
		}

	}

}
