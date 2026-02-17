package com.touhouqing.datasentry.cleaning.controller;

import com.touhouqing.datasentry.cleaning.dto.CleaningRollbackConflictResolveRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningRollbackConflictResolveResult;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackConflictRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackRun;
import com.touhouqing.datasentry.cleaning.service.CleaningRollbackService;
import com.touhouqing.datasentry.vo.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/datasentry/cleaning")
public class CleaningRollbackController {

	private final CleaningRollbackService rollbackService;

	@PostMapping("/job-runs/{runId}/rollback")
	public ResponseEntity<ApiResponse<CleaningRollbackRun>> createRollback(@PathVariable Long runId) {
		return ResponseEntity.ok(ApiResponse.success("success", rollbackService.createRollbackRun(runId)));
	}

	@GetMapping("/rollbacks/{rollbackRunId}")
	public ResponseEntity<ApiResponse<CleaningRollbackRun>> getRollback(@PathVariable Long rollbackRunId) {
		return ResponseEntity.ok(ApiResponse.success("success", rollbackService.getRollbackRun(rollbackRunId)));
	}

	@GetMapping("/rollback-conflicts")
	public ResponseEntity<ApiResponse<List<CleaningRollbackConflictRecord>>> listConflicts(
			@RequestParam(required = false) Long rollbackRunId, @RequestParam(required = false) String level,
			@RequestParam(required = false) Integer resolved, @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponse.success("success",
				rollbackService.listConflictRecords(rollbackRunId, level, resolved, limit)));
	}

	@PostMapping("/rollback-conflicts/resolve")
	public ResponseEntity<ApiResponse<CleaningRollbackConflictResolveResult>> resolveConflicts(
			@RequestBody CleaningRollbackConflictResolveRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", rollbackService.resolveConflictRecords(request)));
	}

}
