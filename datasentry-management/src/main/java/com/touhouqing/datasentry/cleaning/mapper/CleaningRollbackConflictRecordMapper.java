package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackConflictRecord;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface CleaningRollbackConflictRecordMapper extends BaseMapper<CleaningRollbackConflictRecord> {

	default int resolveIfUnresolved(Long id) {
		LambdaUpdateWrapper<CleaningRollbackConflictRecord> wrapper = new LambdaUpdateWrapper<CleaningRollbackConflictRecord>()
			.eq(CleaningRollbackConflictRecord::getId, id)
			.eq(CleaningRollbackConflictRecord::getResolved, 0)
			.set(CleaningRollbackConflictRecord::getResolved, 1);
		return update(null, wrapper);
	}

	default int deleteExpired(LocalDateTime expireBefore, int limit) {
		LambdaQueryWrapper<CleaningRollbackConflictRecord> wrapper = new LambdaQueryWrapper<CleaningRollbackConflictRecord>()
			.lt(CleaningRollbackConflictRecord::getCreatedTime, expireBefore)
			.orderByAsc(CleaningRollbackConflictRecord::getId);
		wrapper.last("LIMIT " + limit);
		return delete(wrapper);
	}

}
