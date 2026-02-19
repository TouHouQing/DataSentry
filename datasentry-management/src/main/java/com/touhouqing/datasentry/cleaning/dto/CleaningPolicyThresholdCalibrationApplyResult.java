package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningPolicyThresholdCalibrationApplyResult {

	private Long policyId;

	private Double blockThreshold;

	private Double reviewThreshold;

	private Double l2Threshold;

	private LocalDateTime updatedTime;

}
