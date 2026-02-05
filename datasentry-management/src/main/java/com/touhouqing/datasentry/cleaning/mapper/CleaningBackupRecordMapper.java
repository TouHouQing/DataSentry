package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningBackupRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CleaningBackupRecordMapper extends BaseMapper<CleaningBackupRecord> {

	default List<CleaningBackupRecord> findByRunAfterId(Long jobRunId, Long lastId, int limit) {
		LambdaQueryWrapper<CleaningBackupRecord> wrapper = new LambdaQueryWrapper<CleaningBackupRecord>()
			.eq(CleaningBackupRecord::getJobRunId, jobRunId)
			.orderByAsc(CleaningBackupRecord::getId);
		if (lastId != null) {
			wrapper.gt(CleaningBackupRecord::getId, lastId);
		}
		wrapper.last("LIMIT " + limit);
		return selectList(wrapper);
	}

}
