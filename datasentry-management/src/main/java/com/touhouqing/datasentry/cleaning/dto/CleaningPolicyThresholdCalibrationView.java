package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningPolicyThresholdCalibrationView {

	private Long policyId;

	private String policyName;

	private Integer feedbackSampleSize;

	private Double disputeRate;

	private Integer shadowSampleSize;

	private Double shadowDiffRate;

	private Double currentBlockThreshold;

	private Double currentReviewThreshold;

	private Double currentL2Threshold;

	private Double recommendedBlockThreshold;

	private Double recommendedReviewThreshold;

	private Double recommendedL2Threshold;

	private String recommendationLevel;

	private List<String> reasons;

	private LocalDateTime generatedTime;

}
