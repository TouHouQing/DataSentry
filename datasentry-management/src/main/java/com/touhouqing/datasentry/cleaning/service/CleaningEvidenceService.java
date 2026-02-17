package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningEvidenceBundleView;
import com.touhouqing.datasentry.cleaning.dto.CleaningEvidenceRollbackView;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyVersionMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackConflictRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackVerifyRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningShadowCompareRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyVersion;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackConflictRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackRun;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackVerifyRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningShadowCompareRecord;
import com.touhouqing.datasentry.exception.InvalidInputException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CleaningEvidenceService {

	private static final int AUDIT_RECORD_LIMIT = 2000;

	private static final int REVIEW_TASK_LIMIT = 2000;

	private static final int SHADOW_COMPARE_LIMIT = 2000;

	private static final int ROLLBACK_RUN_LIMIT = 200;

	private static final int ROLLBACK_VERIFY_LIMIT = 2000;

	private static final int ROLLBACK_CONFLICT_LIMIT = 2000;

	private final CleaningJobRunMapper jobRunMapper;

	private final CleaningPolicyVersionMapper policyVersionMapper;

	private final CleaningRecordMapper recordMapper;

	private final CleaningReviewTaskMapper reviewTaskMapper;

	private final CleaningShadowCompareRecordMapper shadowCompareRecordMapper;

	private final CleaningRollbackRunMapper rollbackRunMapper;

	private final CleaningRollbackVerifyRecordMapper rollbackVerifyRecordMapper;

	private final CleaningRollbackConflictRecordMapper rollbackConflictRecordMapper;

	public CleaningEvidenceBundleView exportByRunId(Long runId) {
		CleaningJobRun run = jobRunMapper.selectById(runId);
		if (run == null) {
			throw new InvalidInputException("清理任务运行实例不存在");
		}
		CleaningPolicyVersion policyVersion = null;
		if (run.getPolicyVersionId() != null) {
			policyVersion = policyVersionMapper.selectById(run.getPolicyVersionId());
		}

		Long totalAuditRecords = recordMapper
			.selectCount(new LambdaQueryWrapper<CleaningRecord>().eq(CleaningRecord::getJobRunId, runId));
		List<CleaningRecord> auditRecords = recordMapper
			.selectList(new LambdaQueryWrapper<CleaningRecord>().eq(CleaningRecord::getJobRunId, runId)
				.orderByAsc(CleaningRecord::getId)
				.last("LIMIT " + AUDIT_RECORD_LIMIT));

		Long totalReviewTasks = reviewTaskMapper
			.selectCount(new LambdaQueryWrapper<CleaningReviewTask>().eq(CleaningReviewTask::getJobRunId, runId));
		List<CleaningReviewTask> reviewTasks = reviewTaskMapper
			.selectList(new LambdaQueryWrapper<CleaningReviewTask>().eq(CleaningReviewTask::getJobRunId, runId)
				.orderByAsc(CleaningReviewTask::getId)
				.last("LIMIT " + REVIEW_TASK_LIMIT));

		Long totalShadowCompareRecords = shadowCompareRecordMapper
			.selectCount(new LambdaQueryWrapper<CleaningShadowCompareRecord>()
				.eq(CleaningShadowCompareRecord::getJobRunId, runId));
		List<CleaningShadowCompareRecord> shadowCompareRecords = shadowCompareRecordMapper
			.selectList(new LambdaQueryWrapper<CleaningShadowCompareRecord>()
				.eq(CleaningShadowCompareRecord::getJobRunId, runId)
				.orderByAsc(CleaningShadowCompareRecord::getId)
				.last("LIMIT " + SHADOW_COMPARE_LIMIT));

		Long totalRollbackRuns = rollbackRunMapper
			.selectCount(new LambdaQueryWrapper<CleaningRollbackRun>().eq(CleaningRollbackRun::getJobRunId, runId));
		List<CleaningRollbackRun> rollbackRuns = rollbackRunMapper
			.selectList(new LambdaQueryWrapper<CleaningRollbackRun>().eq(CleaningRollbackRun::getJobRunId, runId)
				.orderByAsc(CleaningRollbackRun::getId)
				.last("LIMIT " + ROLLBACK_RUN_LIMIT));

		List<CleaningEvidenceRollbackView> rollbackViews = rollbackRuns.stream()
			.map(this::buildRollbackEvidenceView)
			.toList();

		return CleaningEvidenceBundleView.builder()
			.jobRunId(runId)
			.exportedTime(LocalDateTime.now())
			.jobRun(run)
			.policyVersion(policyVersion)
			.totalAuditRecords(defaultLong(totalAuditRecords))
			.totalReviewTasks(defaultLong(totalReviewTasks))
			.totalShadowCompareRecords(defaultLong(totalShadowCompareRecords))
			.totalRollbackRuns(defaultLong(totalRollbackRuns))
			.auditRecordsTruncated(defaultLong(totalAuditRecords) > AUDIT_RECORD_LIMIT)
			.reviewTasksTruncated(defaultLong(totalReviewTasks) > REVIEW_TASK_LIMIT)
			.shadowCompareRecordsTruncated(defaultLong(totalShadowCompareRecords) > SHADOW_COMPARE_LIMIT)
			.rollbackRunsTruncated(defaultLong(totalRollbackRuns) > ROLLBACK_RUN_LIMIT)
			.auditRecords(auditRecords)
			.reviewTasks(reviewTasks)
			.shadowCompareRecords(shadowCompareRecords)
			.rollbackRuns(rollbackViews)
			.build();
	}

	private CleaningEvidenceRollbackView buildRollbackEvidenceView(CleaningRollbackRun rollbackRun) {
		Long rollbackRunId = rollbackRun.getId();
		Long totalVerifyRecords = rollbackVerifyRecordMapper
			.selectCount(new LambdaQueryWrapper<CleaningRollbackVerifyRecord>()
				.eq(CleaningRollbackVerifyRecord::getRollbackRunId, rollbackRunId));
		List<CleaningRollbackVerifyRecord> verifyRecords = rollbackVerifyRecordMapper
			.selectList(new LambdaQueryWrapper<CleaningRollbackVerifyRecord>()
				.eq(CleaningRollbackVerifyRecord::getRollbackRunId, rollbackRunId)
				.orderByAsc(CleaningRollbackVerifyRecord::getId)
				.last("LIMIT " + ROLLBACK_VERIFY_LIMIT));

		Long totalConflictRecords = rollbackConflictRecordMapper
			.selectCount(new LambdaQueryWrapper<CleaningRollbackConflictRecord>()
				.eq(CleaningRollbackConflictRecord::getRollbackRunId, rollbackRunId));
		List<CleaningRollbackConflictRecord> conflictRecords = rollbackConflictRecordMapper
			.selectList(new LambdaQueryWrapper<CleaningRollbackConflictRecord>()
				.eq(CleaningRollbackConflictRecord::getRollbackRunId, rollbackRunId)
				.orderByAsc(CleaningRollbackConflictRecord::getId)
				.last("LIMIT " + ROLLBACK_CONFLICT_LIMIT));

		return CleaningEvidenceRollbackView.builder()
			.rollbackRun(rollbackRun)
			.totalVerifyRecords(defaultLong(totalVerifyRecords))
			.totalConflictRecords(defaultLong(totalConflictRecords))
			.verifyRecordsTruncated(defaultLong(totalVerifyRecords) > ROLLBACK_VERIFY_LIMIT)
			.conflictRecordsTruncated(defaultLong(totalConflictRecords) > ROLLBACK_CONFLICT_LIMIT)
			.verifyRecords(verifyRecords)
			.conflictRecords(conflictRecords)
			.build();
	}

	private Long defaultLong(Long value) {
		return value != null ? value : 0L;
	}

}
