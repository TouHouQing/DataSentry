package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningAlertView;
import com.touhouqing.datasentry.cleaning.dto.CleaningMetricsView;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewOpsView;
import com.touhouqing.datasentry.cleaning.mapper.CleaningCostLedgerMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningDlqMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningCostLedger;
import com.touhouqing.datasentry.cleaning.model.CleaningDlqRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CleaningMetricsService {

	private static final int DLQ_ALERT_THRESHOLD = 100;

	private static final int REVIEW_OVERDUE_SLA_HOURS = 24;

	private static final int REVIEW_HIGH_RISK_BACKLOG_ALERT_THRESHOLD = 20;

	private static final int REVIEW_HANDLE_SAMPLE_LIMIT = 500;

	private final CleaningJobRunMapper jobRunMapper;

	private final CleaningDlqMapper dlqMapper;

	private final CleaningCostLedgerMapper costLedgerMapper;

	private final CleaningReviewTaskMapper reviewTaskMapper;

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
		CleaningReviewOpsView reviewOps = buildReviewOpsSummary();

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
			.reviewOps(reviewOps)
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

		CleaningReviewOpsView reviewOps = buildReviewOpsSummary();
		long overdueTasks = reviewOps.getOverdueTasks() != null ? reviewOps.getOverdueTasks() : 0L;
		if (overdueTasks > 0) {
			alerts.add(CleaningAlertView.builder()
				.level("WARN")
				.code("REVIEW_TASK_OVERDUE")
				.message("人审任务超时数量：" + overdueTasks)
				.createdTime(LocalDateTime.now())
				.build());
		}
		long highRiskPending = reviewOps.getPendingHighRiskTasks() != null ? reviewOps.getPendingHighRiskTasks() : 0L;
		if (highRiskPending >= REVIEW_HIGH_RISK_BACKLOG_ALERT_THRESHOLD) {
			alerts.add(CleaningAlertView.builder()
				.level("WARN")
				.code("REVIEW_HIGH_RISK_BACKLOG")
				.message("高风险待审任务积压：" + highRiskPending)
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

	private CleaningReviewOpsView buildReviewOpsSummary() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime overdueThreshold = now.minusHours(REVIEW_OVERDUE_SLA_HOURS);
		List<CleaningReviewTask> pendingTasks = reviewTaskMapper
			.selectList(new LambdaQueryWrapper<CleaningReviewTask>().eq(CleaningReviewTask::getStatus, "PENDING"));
		long pendingHighRiskTasks = 0L;
		long pendingMediumRiskTasks = 0L;
		long pendingLowRiskTasks = 0L;
		long overdueTasks = 0L;
		for (CleaningReviewTask pendingTask : pendingTasks) {
			if (pendingTask.getCreatedTime() != null && pendingTask.getCreatedTime().isBefore(overdueThreshold)) {
				overdueTasks++;
			}
			if (isHighRiskTask(pendingTask)) {
				pendingHighRiskTasks++;
				continue;
			}
			if (isMediumRiskTask(pendingTask)) {
				pendingMediumRiskTasks++;
				continue;
			}
			pendingLowRiskTasks++;
		}

		List<CleaningReviewTask> handledTasks = reviewTaskMapper
			.selectList(new LambdaQueryWrapper<CleaningReviewTask>().ne(CleaningReviewTask::getStatus, "PENDING")
				.orderByDesc(CleaningReviewTask::getUpdatedTime)
				.last("LIMIT " + REVIEW_HANDLE_SAMPLE_LIMIT));
		double totalHandleMinutes = 0D;
		long validHandleSamples = 0L;
		long slaPassedSamples = 0L;
		for (CleaningReviewTask task : handledTasks) {
			if (task.getCreatedTime() == null || task.getUpdatedTime() == null) {
				continue;
			}
			double handleMinutes = Duration.between(task.getCreatedTime(), task.getUpdatedTime()).toSeconds() / 60D;
			if (handleMinutes < 0) {
				continue;
			}
			totalHandleMinutes += handleMinutes;
			validHandleSamples++;
			if (Duration.between(task.getCreatedTime(), task.getUpdatedTime())
				.compareTo(Duration.ofHours(REVIEW_OVERDUE_SLA_HOURS)) <= 0) {
				slaPassedSamples++;
			}
		}
		Double avgHandleMinutes = validHandleSamples > 0 ? totalHandleMinutes / validHandleSamples : 0D;
		Double slaComplianceRate = validHandleSamples > 0 ? (slaPassedSamples * 100.0D) / validHandleSamples : 100D;

		return CleaningReviewOpsView.builder()
			.pendingTasks((long) pendingTasks.size())
			.pendingHighRiskTasks(pendingHighRiskTasks)
			.pendingMediumRiskTasks(pendingMediumRiskTasks)
			.pendingLowRiskTasks(pendingLowRiskTasks)
			.overdueTasks(overdueTasks)
			.slaHours(REVIEW_OVERDUE_SLA_HOURS)
			.avgHandleMinutes(avgHandleMinutes)
			.slaComplianceRate(slaComplianceRate)
			.build();
	}

	private boolean isHighRiskTask(CleaningReviewTask task) {
		if (task == null) {
			return false;
		}
		if ("BLOCK".equalsIgnoreCase(task.getVerdict())) {
			return true;
		}
		String action = task.getActionSuggested();
		return "DELETE".equalsIgnoreCase(action) || "HARD_DELETE".equalsIgnoreCase(action)
				|| "SOFT_DELETE".equalsIgnoreCase(action);
	}

	private boolean isMediumRiskTask(CleaningReviewTask task) {
		if (task == null) {
			return false;
		}
		if ("REVIEW".equalsIgnoreCase(task.getVerdict())) {
			return true;
		}
		String action = task.getActionSuggested();
		return "WRITEBACK".equalsIgnoreCase(action) || "REVIEW_ONLY".equalsIgnoreCase(action);
	}

	private Long defaultLong(Long value) {
		return value != null ? value : 0L;
	}

}
