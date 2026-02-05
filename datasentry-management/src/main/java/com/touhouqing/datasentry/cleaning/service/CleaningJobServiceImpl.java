package com.touhouqing.datasentry.cleaning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.touhouqing.datasentry.cleaning.dto.CleaningJobCreateRequest;
import com.touhouqing.datasentry.cleaning.enums.CleaningJobMode;
import com.touhouqing.datasentry.cleaning.enums.CleaningJobRunStatus;
import com.touhouqing.datasentry.cleaning.enums.CleaningReviewPolicy;
import com.touhouqing.datasentry.cleaning.enums.CleaningWritebackMode;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningJob;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CleaningJobServiceImpl implements CleaningJobService {

	private final CleaningJobMapper jobMapper;

	private final CleaningJobRunMapper jobRunMapper;

	private final CleaningPolicyResolver policyResolver;

	private final DataSentryProperties dataSentryProperties;

	@Override
	public CleaningJob createJob(CleaningJobCreateRequest request) {
		String mode = resolveEnum(request.getMode(), CleaningJobMode.DRY_RUN.name(), CleaningJobMode.class);
		String writebackMode = resolveEnum(request.getWritebackMode(), CleaningWritebackMode.NONE.name(),
				CleaningWritebackMode.class);
		String reviewPolicy = resolveEnum(request.getReviewPolicy(), CleaningReviewPolicy.NEVER.name(),
				CleaningReviewPolicy.class);
		LocalDateTime now = LocalDateTime.now();
		CleaningJob job = CleaningJob.builder()
			.agentId(request.getAgentId())
			.datasourceId(request.getDatasourceId())
			.tableName(request.getTableName())
			.pkColumnsJson(toJson(request.getPkColumns()))
			.targetColumnsJson(toJson(request.getTargetColumns()))
			.whereSql(request.getWhereSql())
			.policyId(request.getPolicyId())
			.mode(mode)
			.writebackMode(writebackMode)
			.reviewPolicy(reviewPolicy)
			.writebackMappingJson(toJson(request.getWritebackMapping()))
			.batchSize(resolveBatchSize(request.getBatchSize()))
			.enabled(request.getEnabled() != null ? request.getEnabled() : 1)
			.createdTime(now)
			.updatedTime(now)
			.build();
		jobMapper.insert(job);
		return job;
	}

	@Override
	public CleaningJob getJob(Long jobId) {
		return jobMapper.selectById(jobId);
	}

	@Override
	public CleaningJobRun createRun(Long jobId) {
		CleaningJob job = jobMapper.selectById(jobId);
		if (job == null) {
			throw new InvalidInputException("清理任务不存在");
		}
		String policySnapshotJson = toJson(policyResolver.resolveSnapshot(job.getPolicyId()));
		LocalDateTime now = LocalDateTime.now();
		CleaningJobRun run = CleaningJobRun.builder()
			.jobId(jobId)
			.status(CleaningJobRunStatus.QUEUED.name())
			.attempt(0)
			.policySnapshotJson(policySnapshotJson)
			.totalScanned(0L)
			.totalFlagged(0L)
			.totalWritten(0L)
			.totalFailed(0L)
			.createdTime(now)
			.updatedTime(now)
			.build();
		jobRunMapper.insert(run);
		return run;
	}

	@Override
	public CleaningJobRun getRun(Long runId) {
		return jobRunMapper.selectById(runId);
	}

	@Override
	public CleaningJobRun pauseRun(Long runId) {
		jobRunMapper.updateStatusWithoutEnd(runId, CleaningJobRunStatus.PAUSED.name(), LocalDateTime.now());
		return jobRunMapper.selectById(runId);
	}

	@Override
	public CleaningJobRun resumeRun(Long runId) {
		jobRunMapper.updateStatusWithoutEnd(runId, CleaningJobRunStatus.QUEUED.name(), LocalDateTime.now());
		return jobRunMapper.selectById(runId);
	}

	@Override
	public CleaningJobRun cancelRun(Long runId) {
		LocalDateTime now = LocalDateTime.now();
		jobRunMapper.updateStatus(runId, CleaningJobRunStatus.CANCELED.name(), now, now);
		return jobRunMapper.selectById(runId);
	}

	private String resolveEnum(String value, String defaultValue, Class<? extends Enum<?>> enumType) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		for (Enum<?> enumValue : enumType.getEnumConstants()) {
			if (enumValue.name().equalsIgnoreCase(value)) {
				return enumValue.name();
			}
		}
		throw new InvalidInputException("Invalid enum value: " + value);
	}

	private Integer resolveBatchSize(Integer batchSize) {
		if (batchSize != null && batchSize > 0) {
			return batchSize;
		}
		return dataSentryProperties.getCleaning().getBatch().getDefaultBatchSize();
	}

	private String toJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (JsonProcessingException e) {
			throw new InvalidInputException("Invalid json payload");
		}
	}

}
