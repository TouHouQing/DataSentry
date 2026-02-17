package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackConflictRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CleaningRollbackConflictRecordMapper extends BaseMapper<CleaningRollbackConflictRecord> {

	default int resolveIfUnresolved(Long id) {
		LambdaUpdateWrapper<CleaningRollbackConflictRecord> wrapper = new LambdaUpdateWrapper<CleaningRollbackConflictRecord>()
			.eq(CleaningRollbackConflictRecord::getId, id)
			.eq(CleaningRollbackConflictRecord::getResolved, 0)
			.set(CleaningRollbackConflictRecord::getResolved, 1);
		return update(null, wrapper);
	}

}
