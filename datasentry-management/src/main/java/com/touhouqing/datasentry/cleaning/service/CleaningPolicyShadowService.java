package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyShadowSummaryView;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningShadowCompareRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningShadowCompareRecord;
import com.touhouqing.datasentry.exception.InvalidInputException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CleaningPolicyShadowService {

	private static final int DEFAULT_LIMIT = 1000;

	private static final int MAX_LIMIT = 5000;

	private final CleaningPolicyMapper policyMapper;

	private final CleaningShadowCompareRecordMapper shadowCompareRecordMapper;

	public CleaningPolicyShadowSummaryView summarize(Long policyId, Long policyVersionId, Integer limit) {
		if (policyId == null || policyMapper.selectById(policyId) == null) {
			throw new InvalidInputException("策略不存在");
		}
		int safeLimit = resolveLimit(limit);
		LambdaQueryWrapper<CleaningShadowCompareRecord> wrapper = new LambdaQueryWrapper<CleaningShadowCompareRecord>()
			.eq(CleaningShadowCompareRecord::getPolicyId, policyId)
			.orderByDesc(CleaningShadowCompareRecord::getId);
		if (policyVersionId != null) {
			wrapper.eq(CleaningShadowCompareRecord::getPolicyVersionId, policyVersionId);
		}
		wrapper.last("LIMIT " + safeLimit);
		List<CleaningShadowCompareRecord> samples = shadowCompareRecordMapper.selectList(wrapper);
		long totalRecords = samples.size();
		long lowDiffRecords = 0L;
		long mediumDiffRecords = 0L;
		long highDiffRecords = 0L;
		LocalDateTime latestSampleTime = null;
		for (CleaningShadowCompareRecord sample : samples) {
			String diffLevel = sample.getDiffLevel() != null ? sample.getDiffLevel().trim().toUpperCase() : "";
			if ("LOW".equals(diffLevel)) {
				lowDiffRecords++;
			}
			else if ("MEDIUM".equals(diffLevel)) {
				mediumDiffRecords++;
			}
			else if ("HIGH".equals(diffLevel)) {
				highDiffRecords++;
			}
			if (sample.getCreatedTime() != null
					&& (latestSampleTime == null || sample.getCreatedTime().isAfter(latestSampleTime))) {
				latestSampleTime = sample.getCreatedTime();
			}
		}
		long diffRecords = lowDiffRecords + mediumDiffRecords + highDiffRecords;
		double diffRate = totalRecords > 0 ? (diffRecords * 100.0D) / totalRecords : 0D;
		return CleaningPolicyShadowSummaryView.builder()
			.policyId(policyId)
			.policyVersionId(policyVersionId)
			.totalRecords(totalRecords)
			.diffRecords(diffRecords)
			.lowDiffRecords(lowDiffRecords)
			.mediumDiffRecords(mediumDiffRecords)
			.highDiffRecords(highDiffRecords)
			.diffRate(diffRate)
			.latestSampleTime(latestSampleTime)
			.build();
	}

	private int resolveLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

}
