package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.mapper.CleaningBackupRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningDlqMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewFeedbackRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackConflictRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackVerifyRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningShadowCompareRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningLifecycleService {

	private static final int MAX_PURGE_ROUNDS = 1000;

	private final DataSentryProperties dataSentryProperties;

	private final CleaningBackupRecordMapper backupRecordMapper;

	private final CleaningRecordMapper recordMapper;

	private final CleaningReviewTaskMapper reviewTaskMapper;

	private final CleaningReviewFeedbackRecordMapper reviewFeedbackRecordMapper;

	private final CleaningShadowCompareRecordMapper shadowCompareRecordMapper;

	private final CleaningRollbackVerifyRecordMapper rollbackVerifyRecordMapper;

	private final CleaningRollbackConflictRecordMapper rollbackConflictRecordMapper;

	private final CleaningRollbackRunMapper rollbackRunMapper;

	private final CleaningDlqMapper dlqMapper;

	public CleaningLifecyclePurgeResult purgeExpiredData() {
		DataSentryProperties.Cleaning.Lifecycle lifecycle = dataSentryProperties.getCleaning().getLifecycle();
		if (!dataSentryProperties.getCleaning().isEnabled() || !lifecycle.isEnabled()) {
			return CleaningLifecyclePurgeResult.disabled();
		}
		int batchLimit = Math.max(lifecycle.getBatchLimit(), 1);
		LocalDateTime now = LocalDateTime.now();
		long backupDeleted = purgeByBatch(batchLimit, () -> backupRecordMapper
			.deleteExpired(now.minusDays(resolveDays(lifecycle.getBackupRetentionDays())), batchLimit));
		long auditDeleted = purgeByBatch(batchLimit, () -> recordMapper
			.deleteExpired(now.minusDays(resolveDays(lifecycle.getAuditRetentionDays())), batchLimit));
		long reviewDeleted = purgeByBatch(batchLimit, () -> reviewTaskMapper
			.deleteExpired(now.minusDays(resolveDays(lifecycle.getReviewRetentionDays())), batchLimit));
		long reviewFeedbackDeleted = purgeByBatch(batchLimit, () -> reviewFeedbackRecordMapper
			.deleteExpired(now.minusDays(resolveDays(lifecycle.getReviewFeedbackRetentionDays())), batchLimit));
		long shadowDeleted = purgeByBatch(batchLimit, () -> shadowCompareRecordMapper
			.deleteExpired(now.minusDays(resolveDays(lifecycle.getShadowRetentionDays())), batchLimit));
		long rollbackVerifyDeleted = purgeByBatch(batchLimit, () -> rollbackVerifyRecordMapper
			.deleteExpired(now.minusDays(resolveDays(lifecycle.getRollbackRetentionDays())), batchLimit));
		long rollbackConflictDeleted = purgeByBatch(batchLimit, () -> rollbackConflictRecordMapper
			.deleteExpired(now.minusDays(resolveDays(lifecycle.getRollbackRetentionDays())), batchLimit));
		long rollbackRunDeleted = purgeByBatch(batchLimit, () -> rollbackRunMapper
			.deleteExpiredFinished(now.minusDays(resolveDays(lifecycle.getRollbackRetentionDays())), batchLimit));
		long dlqDeleted = purgeByBatch(batchLimit, () -> dlqMapper
			.deleteExpiredFinished(now.minusDays(resolveDays(lifecycle.getDlqRetentionDays())), batchLimit));

		CleaningLifecyclePurgeResult result = new CleaningLifecyclePurgeResult(true, backupDeleted, auditDeleted,
				reviewDeleted, reviewFeedbackDeleted, shadowDeleted, rollbackVerifyDeleted, rollbackConflictDeleted,
				rollbackRunDeleted, dlqDeleted);
		if (result.totalDeleted() > 0) {
			appendPurgeAudit(result, now);
		}
		return result;
	}

	private void appendPurgeAudit(CleaningLifecyclePurgeResult result, LocalDateTime createdTime) {
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("backupDeleted", result.backupDeleted());
		evidence.put("auditDeleted", result.auditDeleted());
		evidence.put("reviewDeleted", result.reviewDeleted());
		evidence.put("reviewFeedbackDeleted", result.reviewFeedbackDeleted());
		evidence.put("shadowDeleted", result.shadowDeleted());
		evidence.put("rollbackVerifyDeleted", result.rollbackVerifyDeleted());
		evidence.put("rollbackConflictDeleted", result.rollbackConflictDeleted());
		evidence.put("rollbackRunDeleted", result.rollbackRunDeleted());
		evidence.put("dlqDeleted", result.dlqDeleted());
		recordMapper.insert(CleaningRecord.builder()
			.agentId(0L)
			.traceId("LIFECYCLE_PURGE")
			.actionTaken("TTL_PURGE")
			.verdict("SYSTEM")
			.evidenceJson(toJsonSafe(evidence))
			.createdTime(createdTime)
			.build());
	}

	private int resolveDays(int configuredDays) {
		if (configuredDays <= 0) {
			return 1;
		}
		return configuredDays;
	}

	private long purgeByBatch(int batchLimit, IntSupplier purgeAction) {
		long total = 0;
		int round = 0;
		while (round < MAX_PURGE_ROUNDS) {
			int deleted = purgeAction.getAsInt();
			if (deleted <= 0) {
				break;
			}
			total += deleted;
			round++;
			if (deleted < batchLimit) {
				break;
			}
		}
		if (round >= MAX_PURGE_ROUNDS) {
			log.warn("Lifecycle purge reached max rounds: {}", MAX_PURGE_ROUNDS);
		}
		return total;
	}

	private String toJsonSafe(Object value) {
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (Exception e) {
			return null;
		}
	}

	public record CleaningLifecyclePurgeResult(boolean enabled, long backupDeleted, long auditDeleted,
			long reviewDeleted, long reviewFeedbackDeleted, long shadowDeleted, long rollbackVerifyDeleted,
			long rollbackConflictDeleted, long rollbackRunDeleted, long dlqDeleted) {

		public long totalDeleted() {
			return backupDeleted + auditDeleted + reviewDeleted + reviewFeedbackDeleted + shadowDeleted
					+ rollbackVerifyDeleted + rollbackConflictDeleted + rollbackRunDeleted + dlqDeleted;
		}

		private static CleaningLifecyclePurgeResult disabled() {
			return new CleaningLifecyclePurgeResult(false, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
		}

	}

}
