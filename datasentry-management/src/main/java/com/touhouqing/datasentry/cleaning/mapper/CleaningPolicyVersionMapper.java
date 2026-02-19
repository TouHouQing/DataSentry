package com.touhouqing.datasentry.cleaning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CleaningPolicyVersionMapper extends BaseMapper<CleaningPolicyVersion> {

	default Integer nextVersionNo(Long policyId) {
		CleaningPolicyVersion latest = selectOne(
				new LambdaQueryWrapper<CleaningPolicyVersion>().eq(CleaningPolicyVersion::getPolicyId, policyId)
					.orderByDesc(CleaningPolicyVersion::getVersionNo)
					.last("LIMIT 1"));
		return latest != null && latest.getVersionNo() != null ? latest.getVersionNo() + 1 : 1;
	}

	default CleaningPolicyVersion findPublished(Long policyId) {
		return selectOne(
				new LambdaQueryWrapper<CleaningPolicyVersion>().eq(CleaningPolicyVersion::getPolicyId, policyId)
					.eq(CleaningPolicyVersion::getStatus, "PUBLISHED")
					.orderByDesc(CleaningPolicyVersion::getVersionNo)
					.last("LIMIT 1"));
	}

	default CleaningPolicyVersion findLatestGray(Long policyId) {
		return selectOne(
				new LambdaQueryWrapper<CleaningPolicyVersion>().eq(CleaningPolicyVersion::getPolicyId, policyId)
					.eq(CleaningPolicyVersion::getStatus, "GRAY")
					.orderByDesc(CleaningPolicyVersion::getVersionNo)
					.last("LIMIT 1"));
	}

	default void demotePublished(Long policyId) {
		update(null,
				new LambdaUpdateWrapper<CleaningPolicyVersion>().eq(CleaningPolicyVersion::getPolicyId, policyId)
					.eq(CleaningPolicyVersion::getStatus, "PUBLISHED")
					.set(CleaningPolicyVersion::getStatus, "ROLLED_BACK"));
	}

}
