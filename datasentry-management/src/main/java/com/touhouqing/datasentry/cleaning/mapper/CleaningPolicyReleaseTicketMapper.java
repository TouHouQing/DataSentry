package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyReleaseTicket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CleaningPolicyReleaseTicketMapper extends BaseMapper<CleaningPolicyReleaseTicket> {

	default CleaningPolicyReleaseTicket findLatestGrayTicket(Long policyId, Long versionId) {
		return selectOne(new LambdaQueryWrapper<CleaningPolicyReleaseTicket>()
			.eq(CleaningPolicyReleaseTicket::getPolicyId, policyId)
			.eq(CleaningPolicyReleaseTicket::getVersionId, versionId)
			.eq(CleaningPolicyReleaseTicket::getAction, "PUBLISH_GRAY")
			.orderByDesc(CleaningPolicyReleaseTicket::getId)
			.last("LIMIT 1"));
	}

}
