package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningReviewOpsView {

	private Long pendingTasks;

	private Long pendingHighRiskTasks;

	private Long pendingMediumRiskTasks;

	private Long pendingLowRiskTasks;

	private Long overdueTasks;

	private Integer slaHours;

	private Double avgHandleMinutes;

	private Double slaComplianceRate;

}
