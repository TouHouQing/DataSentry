package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyThresholdCalibrationApplyResult;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyThresholdCalibrationView;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewFeedbackRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningShadowCompareRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewFeedbackRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningShadowCompareRecord;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CleaningPolicyThresholdCalibrationService {

	private static final int DEFAULT_LIMIT = 300;

	private static final int MAX_LIMIT = 1000;

	private final CleaningPolicyMapper policyMapper;

	private final CleaningReviewFeedbackRecordMapper reviewFeedbackRecordMapper;

	private final CleaningShadowCompareRecordMapper shadowCompareRecordMapper;

	public CleaningPolicyThresholdCalibrationView recommend(Long policyId, Long jobRunId, Long agentId, Integer limit) {
		CleaningPolicy policy = policyMapper.selectById(policyId);
		if (policy == null) {
			throw new InvalidInputException("策略不存在");
		}
		int safeLimit = resolveLimit(limit);
		Map<String, Object> currentConfig = parseConfig(policy.getConfigJson());
		double currentBlockThreshold = normalized01(asDouble(currentConfig.get("blockThreshold"), 0.7D));
		double currentReviewThreshold = normalizedReviewThreshold(
				normalized01(asDouble(currentConfig.get("reviewThreshold"), 0.4D)), currentBlockThreshold);
		double currentL2Threshold = normalized01(asDouble(currentConfig.get("l2Threshold"), 0.6D));

		List<CleaningReviewFeedbackRecord> feedbackSamples = reviewFeedbackRecordMapper.listLatest(jobRunId, agentId,
				safeLimit);
		int feedbackSampleSize = feedbackSamples.size();
		int disputedCount = 0;
		for (CleaningReviewFeedbackRecord sample : feedbackSamples) {
			if ("REJECTED".equals(sample.getFinalStatus()) || "CONFLICT".equals(sample.getFinalStatus())) {
				disputedCount++;
			}
		}
		double disputeRate = feedbackSampleSize > 0 ? (disputedCount * 1.0D / feedbackSampleSize) : 0D;

		List<CleaningShadowCompareRecord> shadowSamples = shadowCompareRecordMapper
			.selectList(new LambdaQueryWrapper<CleaningShadowCompareRecord>()
				.eq(CleaningShadowCompareRecord::getPolicyId, policyId)
				.orderByDesc(CleaningShadowCompareRecord::getId)
				.last("LIMIT " + safeLimit));
		int shadowSampleSize = shadowSamples.size();
		int shadowDiffCount = 0;
		for (CleaningShadowCompareRecord sample : shadowSamples) {
			String level = sample.getDiffLevel() != null ? sample.getDiffLevel().trim().toUpperCase() : "";
			if (!level.isEmpty() && !"NONE".equals(level)) {
				shadowDiffCount++;
			}
		}
		double shadowDiffRate = shadowSampleSize > 0 ? (shadowDiffCount * 1.0D / shadowSampleSize) : 0D;

		double recommendedBlockThreshold = currentBlockThreshold;
		double recommendedReviewThreshold = currentReviewThreshold;
		double recommendedL2Threshold = currentL2Threshold;
		String recommendationLevel = "BALANCED";
		List<String> reasons = new ArrayList<>();
		double riskPressure = Math.max(disputeRate, shadowDiffRate);
		if (feedbackSampleSize < 20 && shadowSampleSize < 100) {
			recommendationLevel = "INSUFFICIENT_SAMPLE";
			reasons.add("样本量不足，建议先积累审核反馈和 Shadow 数据。");
		}
		else if (riskPressure >= 0.35D) {
			recommendationLevel = "CONSERVATIVE";
			recommendedBlockThreshold = normalized01(currentBlockThreshold + 0.05D);
			recommendedReviewThreshold = normalized01(currentReviewThreshold + 0.08D);
			recommendedL2Threshold = normalized01(currentL2Threshold + 0.05D);
			reasons.add("争议率或 Shadow 差异率偏高，建议提高阈值降低误杀。");
		}
		else if (riskPressure <= 0.10D && feedbackSampleSize >= 50 && shadowSampleSize >= 200) {
			recommendationLevel = "AGGRESSIVE";
			recommendedBlockThreshold = normalized01(currentBlockThreshold - 0.03D);
			recommendedReviewThreshold = normalized01(currentReviewThreshold - 0.05D);
			recommendedL2Threshold = normalized01(currentL2Threshold - 0.05D);
			reasons.add("争议率和差异率较低，可适度下调阈值提高自动化处理比例。");
		}
		else {
			if (disputeRate >= 0.2D) {
				recommendedReviewThreshold = normalized01(currentReviewThreshold + 0.03D);
				reasons.add("争议率较高，建议提高 REVIEW 阈值并增加人审缓冲。");
			}
			if (shadowDiffRate >= 0.2D) {
				recommendedL2Threshold = normalized01(currentL2Threshold + 0.03D);
				reasons.add("Shadow 差异较高，建议提高 L2 阈值减少不稳定判定。");
			}
			if (reasons.isEmpty()) {
				reasons.add("当前阈值整体稳定，建议保持并持续观察。");
			}
		}
		recommendedReviewThreshold = normalizedReviewThreshold(recommendedReviewThreshold, recommendedBlockThreshold);

		return CleaningPolicyThresholdCalibrationView.builder()
			.policyId(policyId)
			.policyName(policy.getName())
			.feedbackSampleSize(feedbackSampleSize)
			.disputeRate(disputeRate)
			.shadowSampleSize(shadowSampleSize)
			.shadowDiffRate(shadowDiffRate)
			.currentBlockThreshold(currentBlockThreshold)
			.currentReviewThreshold(currentReviewThreshold)
			.currentL2Threshold(currentL2Threshold)
			.recommendedBlockThreshold(recommendedBlockThreshold)
			.recommendedReviewThreshold(recommendedReviewThreshold)
			.recommendedL2Threshold(recommendedL2Threshold)
			.recommendationLevel(recommendationLevel)
			.reasons(reasons)
			.generatedTime(LocalDateTime.now())
			.build();
	}

	public CleaningPolicyThresholdCalibrationApplyResult apply(Long policyId, Long jobRunId, Long agentId,
			Integer limit) {
		CleaningPolicy policy = policyMapper.selectById(policyId);
		if (policy == null) {
			throw new InvalidInputException("策略不存在");
		}
		CleaningPolicyThresholdCalibrationView recommendation = recommend(policyId, jobRunId, agentId, limit);
		Map<String, Object> config = parseConfig(policy.getConfigJson());
		config.put("blockThreshold", recommendation.getRecommendedBlockThreshold());
		config.put("reviewThreshold", recommendation.getRecommendedReviewThreshold());
		config.put("l2Threshold", recommendation.getRecommendedL2Threshold());
		LocalDateTime now = LocalDateTime.now();
		policyMapper.update(null,
				new LambdaUpdateWrapper<CleaningPolicy>().eq(CleaningPolicy::getId, policyId)
					.set(CleaningPolicy::getConfigJson, toJson(config))
					.set(CleaningPolicy::getUpdatedTime, now));
		return CleaningPolicyThresholdCalibrationApplyResult.builder()
			.policyId(policyId)
			.blockThreshold(recommendation.getRecommendedBlockThreshold())
			.reviewThreshold(recommendation.getRecommendedReviewThreshold())
			.l2Threshold(recommendation.getRecommendedL2Threshold())
			.updatedTime(now)
			.build();
	}

	private int resolveLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
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

	private String toJson(Object value) {
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (Exception e) {
			throw new InvalidInputException("策略配置格式错误");
		}
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

}
