package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningDlqRecord;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CleaningDlqMapper extends BaseMapper<CleaningDlqRecord> {

	default List<CleaningDlqRecord> findRunnable(LocalDateTime now, int limit) {
		LambdaQueryWrapper<CleaningDlqRecord> wrapper = new LambdaQueryWrapper<CleaningDlqRecord>()
			.eq(CleaningDlqRecord::getStatus, "READY")
			.and(condition -> condition.isNull(CleaningDlqRecord::getNextRetryTime)
				.or()
				.le(CleaningDlqRecord::getNextRetryTime, now))
			.orderByAsc(CleaningDlqRecord::getId);
		wrapper.last("LIMIT " + limit);
		return selectList(wrapper);
	}

	default int markDone(Long id) {
		return update(null,
				new LambdaUpdateWrapper<CleaningDlqRecord>().eq(CleaningDlqRecord::getId, id)
					.set(CleaningDlqRecord::getStatus, "DONE")
					.set(CleaningDlqRecord::getUpdatedTime, LocalDateTime.now()));
	}

	default int markDead(Long id) {
		return update(null,
				new LambdaUpdateWrapper<CleaningDlqRecord>().eq(CleaningDlqRecord::getId, id)
					.set(CleaningDlqRecord::getStatus, "DEAD")
					.set(CleaningDlqRecord::getUpdatedTime, LocalDateTime.now()));
	}

	default int increaseRetry(Long id, int retryCount, LocalDateTime nextRetryTime) {
		return update(null,
				new LambdaUpdateWrapper<CleaningDlqRecord>().eq(CleaningDlqRecord::getId, id)
					.set(CleaningDlqRecord::getRetryCount, retryCount)
					.set(CleaningDlqRecord::getNextRetryTime, nextRetryTime)
					.set(CleaningDlqRecord::getUpdatedTime, LocalDateTime.now()));
	}

	default int deleteExpiredFinished(LocalDateTime expireBefore, int limit) {
		LambdaQueryWrapper<CleaningDlqRecord> wrapper = new LambdaQueryWrapper<CleaningDlqRecord>()
			.lt(CleaningDlqRecord::getCreatedTime, expireBefore)
			.ne(CleaningDlqRecord::getStatus, "READY")
			.orderByAsc(CleaningDlqRecord::getId);
		wrapper.last("LIMIT " + limit);
		return delete(wrapper);
	}

}
