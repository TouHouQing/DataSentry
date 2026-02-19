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
public class CleaningPolicyShadowSummaryView {

	private Long policyId;

	private Long policyVersionId;

	private Long totalRecords;

	private Long diffRecords;

	private Long lowDiffRecords;

	private Long mediumDiffRecords;

	private Long highDiffRecords;

	private Double diffRate;

	private LocalDateTime latestSampleTime;

}
