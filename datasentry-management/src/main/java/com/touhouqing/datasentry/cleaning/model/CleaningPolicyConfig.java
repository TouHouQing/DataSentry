package com.touhouqing.datasentry.cleaning.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningPolicyConfig {

	private Double blockThreshold;

	private Double reviewThreshold;

	private Boolean llmEnabled;

	private Double l2Threshold;

	private Boolean shadowEnabled;

	private Double shadowSampleRatio;

	private String highRiskSanitizationMode;

	public double resolvedBlockThreshold() {
		return blockThreshold != null ? blockThreshold : 0.7;
	}

	public double resolvedReviewThreshold() {
		return reviewThreshold != null ? reviewThreshold : 0.4;
	}

	public boolean resolvedLlmEnabled() {
		return llmEnabled == null || llmEnabled;
	}

	public double resolvedL2Threshold() {
		return l2Threshold != null ? l2Threshold : 0.6;
	}

	public boolean resolvedShadowEnabled() {
		return shadowEnabled != null && shadowEnabled;
	}

	public double resolvedShadowSampleRatio() {
		double value = shadowSampleRatio != null ? shadowSampleRatio : 0.0;
		if (value < 0) {
			return 0.0;
		}
		if (value > 1) {
			return 1.0;
		}
		return value;
	}

	public String resolvedHighRiskSanitizationMode() {
		return highRiskSanitizationMode != null ? highRiskSanitizationMode : "MITIGATE";
	}

}
