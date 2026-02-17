package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackVerifyRecord;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface CleaningRollbackVerifyRecordMapper extends BaseMapper<CleaningRollbackVerifyRecord> {

	default int deleteExpired(LocalDateTime expireBefore, int limit) {
		LambdaQueryWrapper<CleaningRollbackVerifyRecord> wrapper = new LambdaQueryWrapper<CleaningRollbackVerifyRecord>()
			.lt(CleaningRollbackVerifyRecord::getCreatedTime, expireBefore)
			.orderByAsc(CleaningRollbackVerifyRecord::getId);
		wrapper.last("LIMIT " + limit);
		return delete(wrapper);
	}

}
