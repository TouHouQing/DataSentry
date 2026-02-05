package com.touhouqing.datasentry.cleaning.controller;

import com.touhouqing.datasentry.cleaning.dto.CleaningReviewBatchRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewBatchResult;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewDecisionRequest;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.service.CleaningReviewService;
import com.touhouqing.datasentry.vo.ApiResponse;
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

	@GetMapping("/reviews")
	public ResponseEntity<ApiResponse<List<CleaningReviewTask>>> listReviews(
			@RequestParam(required = false) String status, @RequestParam(required = false) Long jobRunId,
			@RequestParam(required = false) Long agentId) {
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.listReviews(status, jobRunId, agentId)));
	}

	@GetMapping("/reviews/{id}")
	public ResponseEntity<ApiResponse<CleaningReviewTask>> getReview(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.getReview(id)));
	}

	@PostMapping("/reviews/{id}/approve")
	public ResponseEntity<ApiResponse<CleaningReviewTask>> approve(@PathVariable Long id,
			@RequestBody @Valid CleaningReviewDecisionRequest request) {
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
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.batchApprove(request)));
	}

	@PostMapping("/reviews/batch-reject")
	public ResponseEntity<ApiResponse<CleaningReviewBatchResult>> batchReject(
			@RequestBody @Valid CleaningReviewBatchRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", reviewService.batchReject(request)));
	}

}
