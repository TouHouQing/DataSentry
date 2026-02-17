package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.dto.CleaningJobCreateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningBudgetView;
import com.touhouqing.datasentry.cleaning.dto.CleaningEvidenceBundleView;
import com.touhouqing.datasentry.cleaning.model.CleaningCostLedger;
import com.touhouqing.datasentry.cleaning.model.CleaningJob;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;

import java.util.List;

public interface CleaningJobService {

	CleaningJob createJob(CleaningJobCreateRequest request);

	CleaningJob updateJob(Long jobId, CleaningJobCreateRequest request);

	void deleteJob(Long jobId);

	CleaningJob getJob(Long jobId);

	List<CleaningJob> listJobs(Long agentId, Long datasourceId, Integer enabled);

	CleaningJobRun createRun(Long jobId);

	CleaningJobRun getRun(Long runId);

	List<CleaningJobRun> listRuns(Long jobId, String status);

	CleaningJobRun pauseRun(Long runId);

	CleaningJobRun resumeRun(Long runId);

	CleaningJobRun cancelRun(Long runId);

	CleaningBudgetView getBudget(Long runId);

	List<CleaningCostLedger> listCostLedger(Long jobRunId, String traceId, String channel);

	CleaningEvidenceBundleView exportEvidenceBundle(Long runId);

}
