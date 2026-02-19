package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface CleaningReviewTaskMapper extends BaseMapper<CleaningReviewTask> {

	default int updateStatusWithVersion(Long id, Integer version, String status, String reviewer, String reason,
			LocalDateTime now) {
		LambdaUpdateWrapper<CleaningReviewTask> wrapper = new LambdaUpdateWrapper<CleaningReviewTask>()
			.eq(CleaningReviewTask::getId, id)
			.eq(CleaningReviewTask::getVersion, version)
			.eq(CleaningReviewTask::getStatus, "PENDING")
			.set(CleaningReviewTask::getStatus, status)
			.set(CleaningReviewTask::getReviewer, reviewer)
			.set(CleaningReviewTask::getReviewReason, reason)
			.set(CleaningReviewTask::getUpdatedTime, now)
			.setSql("version = version + 1");
		return update(null, wrapper);
	}

	default int updateStatusIfMatch(Long id, String fromStatus, String toStatus, String reviewer, String reason,
			LocalDateTime now) {
		LambdaUpdateWrapper<CleaningReviewTask> wrapper = new LambdaUpdateWrapper<CleaningReviewTask>()
			.eq(CleaningReviewTask::getId, id)
			.eq(CleaningReviewTask::getStatus, fromStatus)
			.set(CleaningReviewTask::getStatus, toStatus)
			.set(CleaningReviewTask::getReviewer, reviewer)
			.set(CleaningReviewTask::getReviewReason, reason)
			.set(CleaningReviewTask::getUpdatedTime, now)
			.setSql("version = version + 1");
		return update(null, wrapper);
	}

	default int markEscalatedIfPending(Long id, String reviewer, String reason, LocalDateTime now) {
		LambdaUpdateWrapper<CleaningReviewTask> wrapper = new LambdaUpdateWrapper<CleaningReviewTask>()
			.eq(CleaningReviewTask::getId, id)
			.eq(CleaningReviewTask::getStatus, "PENDING")
			.set(CleaningReviewTask::getReviewer, reviewer)
			.set(CleaningReviewTask::getReviewReason, reason)
			.set(CleaningReviewTask::getUpdatedTime, now)
			.setSql("version = version + 1");
		return update(null, wrapper);
	}

	default int rejudgeIfPending(Long id, Integer version, String reviewer, String reason, String verdict,
			String actionSuggested, String sanitizedPreview, String writebackPayloadJson, LocalDateTime now) {
		LambdaUpdateWrapper<CleaningReviewTask> wrapper = new LambdaUpdateWrapper<CleaningReviewTask>()
			.eq(CleaningReviewTask::getId, id)
			.eq(CleaningReviewTask::getVersion, version)
			.eq(CleaningReviewTask::getStatus, "PENDING")
			.set(CleaningReviewTask::getReviewer, reviewer)
			.set(CleaningReviewTask::getReviewReason, reason)
			.set(CleaningReviewTask::getVerdict, verdict)
			.set(CleaningReviewTask::getActionSuggested, actionSuggested)
			.set(CleaningReviewTask::getSanitizedPreview, sanitizedPreview)
			.set(CleaningReviewTask::getWritebackPayloadJson, writebackPayloadJson)
			.set(CleaningReviewTask::getUpdatedTime, now)
			.setSql("version = version + 1");
		return update(null, wrapper);
	}

	default int transferIfPending(Long id, Integer version, String reviewer, String reason, LocalDateTime now) {
		LambdaUpdateWrapper<CleaningReviewTask> wrapper = new LambdaUpdateWrapper<CleaningReviewTask>()
			.eq(CleaningReviewTask::getId, id)
			.eq(CleaningReviewTask::getVersion, version)
			.eq(CleaningReviewTask::getStatus, "PENDING")
			.set(CleaningReviewTask::getReviewer, reviewer)
			.set(CleaningReviewTask::getReviewReason, reason)
			.set(CleaningReviewTask::getUpdatedTime, now)
			.setSql("version = version + 1");
		return update(null, wrapper);
	}

	default int deleteExpired(LocalDateTime expireBefore, int limit) {
		return delete(new LambdaQueryWrapper<CleaningReviewTask>().lt(CleaningReviewTask::getCreatedTime, expireBefore)
			.orderByAsc(CleaningReviewTask::getId)
			.last("LIMIT " + limit));
	}

}
