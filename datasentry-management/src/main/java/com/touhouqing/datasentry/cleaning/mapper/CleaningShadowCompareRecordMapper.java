package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningShadowCompareRecord;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface CleaningShadowCompareRecordMapper extends BaseMapper<CleaningShadowCompareRecord> {

	default int deleteExpired(LocalDateTime expireBefore, int limit) {
		LambdaQueryWrapper<CleaningShadowCompareRecord> wrapper = new LambdaQueryWrapper<CleaningShadowCompareRecord>()
			.lt(CleaningShadowCompareRecord::getCreatedTime, expireBefore)
			.orderByAsc(CleaningShadowCompareRecord::getId);
		wrapper.last("LIMIT " + limit);
		return delete(wrapper);
	}

}
