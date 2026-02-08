package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningAlertView;
import com.touhouqing.datasentry.cleaning.dto.CleaningMetricsView;
import com.touhouqing.datasentry.cleaning.mapper.CleaningCostLedgerMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningDlqMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningCostLedger;
import com.touhouqing.datasentry.cleaning.model.CleaningDlqRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CleaningMetricsService {

	private static final int DLQ_ALERT_THRESHOLD = 100;

	private final CleaningJobRunMapper jobRunMapper;

	private final CleaningDlqMapper dlqMapper;

	private final CleaningCostLedgerMapper costLedgerMapper;

	private final CleaningOpsStateService opsStateService;

	public CleaningMetricsView summary() {
		Long totalRuns = jobRunMapper.selectCount(new LambdaQueryWrapper<>());
		Long runningRuns = jobRunMapper
			.selectCount(new LambdaQueryWrapper<CleaningJobRun>().eq(CleaningJobRun::getStatus, "RUNNING"));
		Long pausedRuns = jobRunMapper
			.selectCount(new LambdaQueryWrapper<CleaningJobRun>().eq(CleaningJobRun::getStatus, "PAUSED"));
		Long hardExceededRuns = jobRunMapper
			.selectCount(new LambdaQueryWrapper<CleaningJobRun>().eq(CleaningJobRun::getBudgetStatus, "HARD_EXCEEDED"));
		Long totalDlq = dlqMapper.selectCount(new LambdaQueryWrapper<>());
		Long readyDlq = dlqMapper
			.selectCount(new LambdaQueryWrapper<CleaningDlqRecord>().eq(CleaningDlqRecord::getStatus, "READY"));

		List<CleaningCostLedger> ledgers = costLedgerMapper.selectList(new LambdaQueryWrapper<>());
		BigDecimal totalCost = BigDecimal.ZERO;
		BigDecimal onlineCost = BigDecimal.ZERO;
		BigDecimal batchCost = BigDecimal.ZERO;
		for (CleaningCostLedger ledger : ledgers) {
			BigDecimal amount = ledger.getCostAmount() != null ? ledger.getCostAmount() : BigDecimal.ZERO;
			totalCost = totalCost.add(amount);
			if ("ONLINE".equalsIgnoreCase(ledger.getChannel()) || "ANALYSIS".equalsIgnoreCase(ledger.getChannel())) {
				onlineCost = onlineCost.add(amount);
			}
			if ("BATCH".equalsIgnoreCase(ledger.getChannel())) {
				batchCost = batchCost.add(amount);
			}
		}

		return CleaningMetricsView.builder()
			.totalRuns(defaultLong(totalRuns))
			.runningRuns(defaultLong(runningRuns))
			.pausedRuns(defaultLong(pausedRuns))
			.hardExceededRuns(defaultLong(hardExceededRuns))
			.totalDlq(defaultLong(totalDlq))
			.readyDlq(defaultLong(readyDlq))
			.totalCost(totalCost)
			.onlineCost(onlineCost)
			.batchCost(batchCost)
			.lastPricingSyncTime(opsStateService.getLastPricingSyncTime())
			.pricingSyncFailureCount(opsStateService.getPricingSyncFailureCount())
			.webhookPushSuccessCount(opsStateService.getWebhookPushSuccessCount())
			.webhookPushFailureCount(opsStateService.getWebhookPushFailureCount())
			.l2ProviderStatus(opsStateService.getL2ProviderStatus())
			.onnxModelLoadSuccessCount(opsStateService.getOnnxModelLoadSuccessCount())
			.onnxModelLoadFailureCount(opsStateService.getOnnxModelLoadFailureCount())
			.onnxInferenceSuccessCount(opsStateService.getOnnxInferenceSuccessCount())
			.onnxInferenceFailureCount(opsStateService.getOnnxInferenceFailureCount())
			.onnxFallbackCount(opsStateService.getOnnxFallbackCount())
			.onnxInferenceAvgLatencyMs(opsStateService.getOnnxInferenceAvgLatencyMs())
			.onnxInferenceP95LatencyMs(opsStateService.getOnnxInferenceP95LatencyMs())
			.onnxRuntimeVersion(opsStateService.getOnnxRuntimeVersion())
			.onnxModelSignature(opsStateService.getOnnxModelSignature())
			.cloudInferenceSuccessCount(opsStateService.getCloudInferenceSuccessCount())
			.cloudInferenceFailureCount(opsStateService.getCloudInferenceFailureCount())
			.cloudFallbackCount(opsStateService.getCloudFallbackCount())
			.cloudInferenceAvgLatencyMs(opsStateService.getCloudInferenceAvgLatencyMs())
			.cloudInferenceP95LatencyMs(opsStateService.getCloudInferenceP95LatencyMs())
			.build();
	}

	public List<CleaningAlertView> alerts() {
		List<CleaningAlertView> alerts = new ArrayList<>();
		Long hardExceededRuns = jobRunMapper
			.selectCount(new LambdaQueryWrapper<CleaningJobRun>().eq(CleaningJobRun::getBudgetStatus, "HARD_EXCEEDED"));
		if (hardExceededRuns != null && hardExceededRuns > 0) {
			alerts.add(CleaningAlertView.builder()
				.level("WARN")
				.code("BUDGET_HARD_EXCEEDED")
				.message("存在超出硬预算阈值的任务运行实例：" + hardExceededRuns)
				.createdTime(LocalDateTime.now())
				.build());
		}
		Long readyDlq = dlqMapper
			.selectCount(new LambdaQueryWrapper<CleaningDlqRecord>().eq(CleaningDlqRecord::getStatus, "READY"));
		if (readyDlq != null && readyDlq >= DLQ_ALERT_THRESHOLD) {
			alerts.add(CleaningAlertView.builder()
				.level("WARN")
				.code("DLQ_BACKLOG")
				.message("DLQ 待处理积压超过阈值：" + readyDlq)
				.createdTime(LocalDateTime.now())
				.build());
		}

		long pricingSyncFailures = opsStateService.getPricingSyncFailureCount();
		if (pricingSyncFailures > 0) {
			alerts.add(CleaningAlertView.builder()
				.level("WARN")
				.code("PRICING_SYNC_FAILED")
				.message("价格同步累计失败次数：" + pricingSyncFailures)
				.createdTime(LocalDateTime.now())
				.build());
		}

		String l2ProviderStatus = opsStateService.getL2ProviderStatus();
		String normalizedProviderStatus = l2ProviderStatus != null ? l2ProviderStatus.toUpperCase(Locale.ROOT) : "";
		if (normalizedProviderStatus.startsWith("ONNX/DEGRADED")) {
			alerts.add(CleaningAlertView.builder()
				.level("WARN")
				.code("L2_ONNX_DEGRADED")
				.message("L2 ONNX Provider 当前处于降级状态：" + l2ProviderStatus)
				.createdTime(LocalDateTime.now())
				.build());
		}
		if (normalizedProviderStatus.startsWith("CLOUD_API/DEGRADED")) {
			alerts.add(CleaningAlertView.builder()
				.level("WARN")
				.code("L2_CLOUD_API_DEGRADED")
				.message("L2 Cloud API Provider 当前处于降级状态：" + l2ProviderStatus)
				.createdTime(LocalDateTime.now())
				.build());
		}
		if (alerts.isEmpty()) {
			alerts.add(CleaningAlertView.builder()
				.level("INFO")
				.code("SYSTEM_OK")
				.message("当前未发现预算或 DLQ 风险")
				.createdTime(LocalDateTime.now())
				.build());
		}
		return alerts;
	}

	private Long defaultLong(Long value) {
		return value != null ? value : 0L;
	}

}
