package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface CleaningRecordMapper extends BaseMapper<CleaningRecord> {

	default int deleteExpired(LocalDateTime expireBefore, int limit) {
		LambdaQueryWrapper<CleaningRecord> wrapper = new LambdaQueryWrapper<CleaningRecord>()
			.lt(CleaningRecord::getCreatedTime, expireBefore)
			.orderByAsc(CleaningRecord::getId);
		wrapper.last("LIMIT " + limit);
		return delete(wrapper);
	}

}
