package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningPolicyCopilotSuggestionView {

	private Long policyId;

	private String policyName;

	private Integer sampleSize;

	private Double disputeRate;

	private String recommendationLevel;

	private String recommendedDefaultAction;

	private Map<String, Object> recommendedConfig;

	private List<Long> recommendedRuleIds;

	private List<String> insights;

	private LocalDateTime generatedTime;

}
