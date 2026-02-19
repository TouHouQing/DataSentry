package com.touhouqing.datasentry.cleaning.controller;

import com.touhouqing.datasentry.cleaning.dto.CleaningJobCreateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningBudgetView;
import com.touhouqing.datasentry.cleaning.dto.CleaningEvidenceBundleView;
import com.touhouqing.datasentry.cleaning.enums.CleaningPermissionCode;
import com.touhouqing.datasentry.cleaning.model.CleaningCostLedger;
import com.touhouqing.datasentry.cleaning.model.CleaningJob;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.cleaning.security.CleaningPermissionGuard;
import com.touhouqing.datasentry.cleaning.service.CleaningJobService;
import com.touhouqing.datasentry.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/datasentry/cleaning")
public class CleaningJobController {

	private final CleaningJobService jobService;

	private final CleaningPermissionGuard permissionGuard;

	@PostMapping("/jobs")
	public ResponseEntity<ApiResponse<CleaningJob>> createJob(@RequestBody @Valid CleaningJobCreateRequest request) {
		requireWritebackPermissionIfNeeded(request);
		CleaningJob job = jobService.createJob(request);
		return ResponseEntity.ok(ApiResponse.success("success", job));
	}

	@PutMapping("/jobs/{jobId}")
	public ResponseEntity<ApiResponse<CleaningJob>> updateJob(@PathVariable Long jobId,
			@RequestBody @Valid CleaningJobCreateRequest request) {
		requireWritebackPermissionIfNeeded(request);
		CleaningJob job = jobService.updateJob(jobId, request);
		return ResponseEntity.ok(ApiResponse.success("success", job));
	}

	@DeleteMapping("/jobs/{jobId}")
	public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable Long jobId) {
		jobService.deleteJob(jobId);
		return ResponseEntity.ok(ApiResponse.success("success"));
	}

	@GetMapping("/jobs/{jobId}")
	public ResponseEntity<ApiResponse<CleaningJob>> getJob(@PathVariable Long jobId) {
		CleaningJob job = jobService.getJob(jobId);
		return ResponseEntity.ok(ApiResponse.success("success", job));
	}

	@GetMapping("/jobs")
	public ResponseEntity<ApiResponse<List<CleaningJob>>> listJobs(@RequestParam(required = false) Long agentId,
			@RequestParam(required = false) Long datasourceId, @RequestParam(required = false) Integer enabled) {
		return ResponseEntity.ok(ApiResponse.success("success", jobService.listJobs(agentId, datasourceId, enabled)));
	}

	@PostMapping("/jobs/{jobId}/runs")
	public ResponseEntity<ApiResponse<CleaningJobRun>> createRun(@PathVariable Long jobId) {
		CleaningJobRun run = jobService.createRun(jobId);
		return ResponseEntity.ok(ApiResponse.success("success", run));
	}

	@GetMapping("/job-runs/{runId}")
	public ResponseEntity<ApiResponse<CleaningJobRun>> getRun(@PathVariable Long runId) {
		CleaningJobRun run = jobService.getRun(runId);
		return ResponseEntity.ok(ApiResponse.success("success", run));
	}

	@GetMapping("/job-runs")
	public ResponseEntity<ApiResponse<List<CleaningJobRun>>> listRuns(@RequestParam(required = false) Long jobId,
			@RequestParam(required = false) String status) {
		return ResponseEntity.ok(ApiResponse.success("success", jobService.listRuns(jobId, status)));
	}

	@PostMapping("/job-runs/{runId}/pause")
	public ResponseEntity<ApiResponse<CleaningJobRun>> pauseRun(@PathVariable Long runId) {
		CleaningJobRun run = jobService.pauseRun(runId);
		return ResponseEntity.ok(ApiResponse.success("success", run));
	}

	@PostMapping("/job-runs/{runId}/resume")
	public ResponseEntity<ApiResponse<CleaningJobRun>> resumeRun(@PathVariable Long runId) {
		CleaningJobRun run = jobService.resumeRun(runId);
		return ResponseEntity.ok(ApiResponse.success("success", run));
	}

	@PostMapping("/job-runs/{runId}/cancel")
	public ResponseEntity<ApiResponse<CleaningJobRun>> cancelRun(@PathVariable Long runId) {
		CleaningJobRun run = jobService.cancelRun(runId);
		return ResponseEntity.ok(ApiResponse.success("success", run));
	}

	@GetMapping("/job-runs/{runId}/budget")
	public ResponseEntity<ApiResponse<CleaningBudgetView>> budget(@PathVariable Long runId) {
		return ResponseEntity.ok(ApiResponse.success("success", jobService.getBudget(runId)));
	}

	@GetMapping("/cost-ledger")
	public ResponseEntity<ApiResponse<List<CleaningCostLedger>>> costLedger(
			@RequestParam(required = false) Long jobRunId, @RequestParam(required = false) String traceId,
			@RequestParam(required = false) String channel) {
		return ResponseEntity.ok(ApiResponse.success("success", jobService.listCostLedger(jobRunId, traceId, channel)));
	}

	@GetMapping("/job-runs/{runId}/evidence-bundle")
	public ResponseEntity<ApiResponse<CleaningEvidenceBundleView>> exportEvidenceBundle(@PathVariable Long runId) {
		permissionGuard.require(CleaningPermissionCode.AUDIT_EXPORT);
		return ResponseEntity.ok(ApiResponse.success("success", jobService.exportEvidenceBundle(runId)));
	}

	private void requireWritebackPermissionIfNeeded(CleaningJobCreateRequest request) {
		if (request == null) {
			return;
		}
		String mode = request.getMode();
		String writebackMode = request.getWritebackMode();
		boolean writebackRequested = "WRITEBACK".equalsIgnoreCase(mode)
				|| (writebackMode != null && !"NONE".equalsIgnoreCase(writebackMode));
		if (writebackRequested) {
			permissionGuard.require(CleaningPermissionCode.WRITEBACK_EXECUTE);
			if ("HARD_DELETE".equalsIgnoreCase(writebackMode)) {
				permissionGuard.require(CleaningPermissionCode.DELETE_HARD);
			}
		}
	}

}
