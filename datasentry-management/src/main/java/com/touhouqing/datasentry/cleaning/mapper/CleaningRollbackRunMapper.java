package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.enums.CleaningRollbackStatus;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackRun;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CleaningRollbackRunMapper extends BaseMapper<CleaningRollbackRun> {

	default List<CleaningRollbackRun> findRunnableRuns(int limit) {
		LambdaQueryWrapper<CleaningRollbackRun> wrapper = new LambdaQueryWrapper<CleaningRollbackRun>()
			.eq(CleaningRollbackRun::getStatus, "READY")
			.orderByAsc(CleaningRollbackRun::getId);
		wrapper.last("LIMIT " + limit);
		return selectList(wrapper);
	}

	default int startRun(Long id, LocalDateTime now) {
		LambdaUpdateWrapper<CleaningRollbackRun> wrapper = new LambdaUpdateWrapper<CleaningRollbackRun>()
			.eq(CleaningRollbackRun::getId, id)
			.eq(CleaningRollbackRun::getStatus, "READY")
			.set(CleaningRollbackRun::getStatus, "RUNNING")
			.set(CleaningRollbackRun::getStartedTime, now)
			.set(CleaningRollbackRun::getUpdatedTime, now);
		return update(null, wrapper);
	}

	default int updateProgress(Long id, Long checkpointId, Long totalTarget, Long totalSuccess, Long totalFailed,
			LocalDateTime now) {
		LambdaUpdateWrapper<CleaningRollbackRun> wrapper = new LambdaUpdateWrapper<CleaningRollbackRun>()
			.eq(CleaningRollbackRun::getId, id)
			.set(CleaningRollbackRun::getCheckpointId, checkpointId)
			.set(CleaningRollbackRun::getTotalTarget, totalTarget)
			.set(CleaningRollbackRun::getTotalSuccess, totalSuccess)
			.set(CleaningRollbackRun::getTotalFailed, totalFailed)
			.set(CleaningRollbackRun::getUpdatedTime, now);
		return update(null, wrapper);
	}

	default int updateStatus(Long id, String status, LocalDateTime endTime, LocalDateTime now) {
		LambdaUpdateWrapper<CleaningRollbackRun> wrapper = new LambdaUpdateWrapper<CleaningRollbackRun>()
			.eq(CleaningRollbackRun::getId, id)
			.set(CleaningRollbackRun::getStatus, status)
			.set(CleaningRollbackRun::getEndedTime, endTime)
			.set(CleaningRollbackRun::getUpdatedTime, now);
		return update(null, wrapper);
	}

	default int updateVerification(Long id, String verifyStatus, String conflictLevelSummary, LocalDateTime now) {
		LambdaUpdateWrapper<CleaningRollbackRun> wrapper = new LambdaUpdateWrapper<CleaningRollbackRun>()
			.eq(CleaningRollbackRun::getId, id)
			.set(CleaningRollbackRun::getVerifyStatus, verifyStatus)
			.set(CleaningRollbackRun::getConflictLevelSummary, conflictLevelSummary)
			.set(CleaningRollbackRun::getUpdatedTime, now);
		return update(null, wrapper);
	}

	default int deleteExpiredFinished(LocalDateTime expireBefore, int limit) {
		LambdaQueryWrapper<CleaningRollbackRun> wrapper = new LambdaQueryWrapper<CleaningRollbackRun>()
			.lt(CleaningRollbackRun::getCreatedTime, expireBefore)
			.notIn(CleaningRollbackRun::getStatus, CleaningRollbackStatus.READY.name(),
					CleaningRollbackStatus.RUNNING.name())
			.orderByAsc(CleaningRollbackRun::getId);
		wrapper.last("LIMIT " + limit);
		return delete(wrapper);
	}

}
