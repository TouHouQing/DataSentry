package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningMetricsView {

	private Long totalRuns;

	private Long runningRuns;

	private Long pausedRuns;

	private Long hardExceededRuns;

	private Long totalDlq;

	private Long readyDlq;

	private BigDecimal totalCost;

	private BigDecimal onlineCost;

	private BigDecimal batchCost;

	private LocalDateTime lastPricingSyncTime;

	private Long pricingSyncFailureCount;

	private Long webhookPushSuccessCount;

	private Long webhookPushFailureCount;

	private String l2ProviderStatus;

	private Long onnxModelLoadSuccessCount;

	private Long onnxModelLoadFailureCount;

	private Long onnxInferenceSuccessCount;

	private Long onnxInferenceFailureCount;

	private Long onnxFallbackCount;

	private Double onnxInferenceAvgLatencyMs;

	private Long onnxInferenceP95LatencyMs;

	private String onnxRuntimeVersion;

	private String onnxModelSignature;

	private Long cloudInferenceSuccessCount;

	private Long cloudInferenceFailureCount;

	private Long cloudFallbackCount;

	private Double cloudInferenceAvgLatencyMs;

	private Long cloudInferenceP95LatencyMs;

	private CleaningReviewOpsView reviewOps;

}
