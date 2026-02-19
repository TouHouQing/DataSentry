package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningReviewOptimizationView {

	private Integer totalSamples;

	private List<DisputedRuleView> disputedRules;

	private List<ThresholdSuggestionView> thresholdSuggestions;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DisputedRuleView {

		private String category;

		private String actionSuggested;

		private Integer total;

		private Integer rejected;

		private Integer conflict;

		private BigDecimal disputeRate;

	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ThresholdSuggestionView {

		private String category;

		private String suggestion;

		private String reason;

		private BigDecimal disputeRate;

	}

}
