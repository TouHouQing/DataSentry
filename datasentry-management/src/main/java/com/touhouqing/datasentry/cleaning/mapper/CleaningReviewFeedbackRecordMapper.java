package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewFeedbackRecord;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CleaningReviewFeedbackRecordMapper extends BaseMapper<CleaningReviewFeedbackRecord> {

	default List<CleaningReviewFeedbackRecord> listLatest(Long jobRunId, Long agentId, int limit) {
		LambdaQueryWrapper<CleaningReviewFeedbackRecord> wrapper = new LambdaQueryWrapper<>();
		if (jobRunId != null) {
			wrapper.eq(CleaningReviewFeedbackRecord::getJobRunId, jobRunId);
		}
		if (agentId != null) {
			wrapper.eq(CleaningReviewFeedbackRecord::getAgentId, agentId);
		}
		wrapper.orderByDesc(CleaningReviewFeedbackRecord::getId);
		wrapper.last("LIMIT " + limit);
		return selectList(wrapper);
	}

	default int deleteExpired(LocalDateTime expireBefore, int limit) {
		LambdaQueryWrapper<CleaningReviewFeedbackRecord> wrapper = new LambdaQueryWrapper<CleaningReviewFeedbackRecord>()
			.lt(CleaningReviewFeedbackRecord::getCreatedTime, expireBefore)
			.orderByAsc(CleaningReviewFeedbackRecord::getId);
		wrapper.last("LIMIT " + limit);
		return delete(wrapper);
	}

}
