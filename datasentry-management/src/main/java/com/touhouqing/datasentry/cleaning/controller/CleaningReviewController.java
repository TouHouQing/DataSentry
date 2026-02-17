package com.touhouqing.datasentry.cleaning.controller;

import com.touhouqing.datasentry.cleaning.dto.CleaningReviewBatchRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewBatchResult;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewDecisionRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateResult;
import com.touhouqing.datasentry.cleaning.enums.CleaningPermissionCode;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewFeedbackRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.security.CleaningPermissionGuard;
import com.touhouqing.datasentry.cleaning.service.CleaningReviewService;
import com.touhouqing.datasentry.vo.ApiResponse;
import com.touhouqing.datasentry.vo.PageResponse;
import com.touhouqing.datasentry.vo.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/datasentry/cleaning")
public class CleaningReviewController {

	private final CleaningReviewService reviewService;

	private final CleaningPermissionGuard permissionGuard;

	@GetMapping("/reviews")
	public ResponseEntity<PageResponse<List<CleaningReviewTask>>> listReviews(
			@RequestParam(required = false) String status, @RequestParam(required = false) Long jobRunId,
			@RequestParam(required = false) Long agentId, @RequestParam(required = false) Integer pageNum,
			@RequestParam(required = false) Integer pageSize) {
		PageResult<CleaningReviewTask> pageResult = reviewService.listReviews(status, jobRunId, agentId, pageNum,
				pageSize);
		return ResponseEntity.ok(PageResponse.success("success", pageResult.getData(), pageResult.getTotal(),
				pageResult.getPageNum(), pageResult.getPageSize(), pageResult.getTotalPages()));
	}

	@GetMapping("/reviews/feedback-samples")
	public ResponseEntity<ApiResponse<List<CleaningReviewFeedbackRecord>>> listFeedbackSamples(
			@RequestParam(required = false) Long jobRunId, @RequestParam(required = false) Long agentId,
			@RequestParam(required = false) Integer limit) {
		return ResponseEntity
			.ok(ApiResponse.success("success", reviewService.listFeedbackSamples(jobRunId, agentId, limit)));
	}

	@GetMapping("/reviews/{id}")
	public ResponseEntity<ApiResponse<CleaningReviewTask>> getReview(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.getReview(id)));
	}

	@GetMapping("/reviews/overdue")
	public ResponseEntity<ApiResponse<List<CleaningReviewTask>>> listOverdueReviews(
			@RequestParam(required = false) Integer overdueHours, @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.listOverduePending(overdueHours, limit)));
	}

	@PostMapping("/reviews/{id}/approve")
	public ResponseEntity<ApiResponse<CleaningReviewTask>> approve(@PathVariable Long id,
			@RequestBody @Valid CleaningReviewDecisionRequest request) {
		permissionGuard.require(CleaningPermissionCode.WRITEBACK_EXECUTE);
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.approve(id, request)));
	}

	@PostMapping("/reviews/{id}/reject")
	public ResponseEntity<ApiResponse<CleaningReviewTask>> reject(@PathVariable Long id,
			@RequestBody @Valid CleaningReviewDecisionRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.reject(id, request)));
	}

	@PostMapping("/reviews/batch-approve")
	public ResponseEntity<ApiResponse<CleaningReviewBatchResult>> batchApprove(
			@RequestBody @Valid CleaningReviewBatchRequest request) {
		permissionGuard.require(CleaningPermissionCode.WRITEBACK_EXECUTE);
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.batchApprove(request)));
	}

	@PostMapping("/reviews/batch-reject")
	public ResponseEntity<ApiResponse<CleaningReviewBatchResult>> batchReject(
			@RequestBody @Valid CleaningReviewBatchRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.batchReject(request)));
	}

	@PostMapping("/reviews/escalate-overdue")
	public ResponseEntity<ApiResponse<CleaningReviewEscalateResult>> escalateOverdue(
			@RequestBody(required = false) CleaningReviewEscalateRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.escalateOverduePending(request)));
	}

}
