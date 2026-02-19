package com.touhouqing.datasentry.cleaning.controller;

import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyExperimentView;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyPublishRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRollbackVersionRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyTemplateCloneRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyTemplateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyTemplateView;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyRuleUpdateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyVersionView;
import com.touhouqing.datasentry.cleaning.dto.CleaningPolicyView;
import com.touhouqing.datasentry.cleaning.dto.CleaningRuleRequest;
import com.touhouqing.datasentry.cleaning.enums.CleaningPermissionCode;
import com.touhouqing.datasentry.cleaning.security.CleaningPermissionGuard;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.service.CleaningPolicyService;
import com.touhouqing.datasentry.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/datasentry/cleaning")
public class CleaningPolicyController {

	private final CleaningPolicyService policyService;

	private final CleaningPermissionGuard permissionGuard;

	@GetMapping("/policies")
	public ResponseEntity<ApiResponse<List<CleaningPolicyView>>> listPolicies() {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.listPolicies()));
	}

	@GetMapping("/policy-templates")
	public ResponseEntity<ApiResponse<List<CleaningPolicyTemplateView>>> listPolicyTemplates() {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.listPolicyTemplates()));
	}

	@PostMapping("/policy-templates")
	public ResponseEntity<ApiResponse<CleaningPolicyTemplateView>> createPolicyTemplate(
			@RequestBody @Valid CleaningPolicyTemplateRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.createPolicyTemplate(request)));
	}

	@PutMapping("/policy-templates/{templateId}")
	public ResponseEntity<ApiResponse<CleaningPolicyTemplateView>> updatePolicyTemplate(@PathVariable Long templateId,
			@RequestBody @Valid CleaningPolicyTemplateRequest request) {
		return ResponseEntity
			.ok(ApiResponse.success("success", policyService.updatePolicyTemplate(templateId, request)));
	}

	@DeleteMapping("/policy-templates/{templateId}")
	public ResponseEntity<ApiResponse<Void>> deletePolicyTemplate(@PathVariable Long templateId) {
		policyService.deletePolicyTemplate(templateId);
		return ResponseEntity.ok(ApiResponse.success("success"));
	}

	@PostMapping("/policy-templates/{templateId}/clone")
	public ResponseEntity<ApiResponse<CleaningPolicyView>> clonePolicyTemplate(@PathVariable Long templateId,
			@RequestBody @Valid CleaningPolicyTemplateCloneRequest request) {
		return ResponseEntity
			.ok(ApiResponse.success("success", policyService.clonePolicyTemplate(templateId, request)));
	}

	@PostMapping("/policies")
	public ResponseEntity<ApiResponse<CleaningPolicy>> createPolicy(@RequestBody @Valid CleaningPolicyRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.createPolicy(request)));
	}

	@PutMapping("/policies/{policyId}")
	public ResponseEntity<ApiResponse<CleaningPolicy>> updatePolicy(@PathVariable Long policyId,
			@RequestBody @Valid CleaningPolicyRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.updatePolicy(policyId, request)));
	}

	@DeleteMapping("/policies/{policyId}")
	public ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable Long policyId) {
		policyService.deletePolicy(policyId);
		return ResponseEntity.ok(ApiResponse.success("success"));
	}

	@PutMapping("/policies/{policyId}/rules")
	public ResponseEntity<ApiResponse<Void>> updatePolicyRules(@PathVariable Long policyId,
			@RequestBody @Valid CleaningPolicyRuleUpdateRequest request) {
		policyService.updatePolicyRules(policyId, request);
		return ResponseEntity.ok(ApiResponse.success("success"));
	}

	@GetMapping("/policies/{policyId}/versions")
	public ResponseEntity<ApiResponse<List<CleaningPolicyVersionView>>> listPolicyVersions(
			@PathVariable Long policyId) {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.listPolicyVersions(policyId)));
	}

	@PostMapping("/policies/{policyId}/publish")
	public ResponseEntity<ApiResponse<CleaningPolicyVersionView>> publishPolicy(@PathVariable Long policyId,
			@RequestBody(required = false) CleaningPolicyPublishRequest request) {
		permissionGuard.require(CleaningPermissionCode.POLICY_PUBLISH);
		return ResponseEntity.ok(ApiResponse.success("success", policyService.publishPolicy(policyId, request)));
	}

	@PostMapping("/policies/{policyId}/rollback-version")
	public ResponseEntity<ApiResponse<CleaningPolicyVersionView>> rollbackPolicyVersion(@PathVariable Long policyId,
			@RequestBody @Valid CleaningPolicyRollbackVersionRequest request) {
		permissionGuard.require(CleaningPermissionCode.POLICY_PUBLISH);
		return ResponseEntity.ok(ApiResponse.success("success", policyService.rollbackToVersion(policyId, request)));
	}

	@GetMapping("/policies/{policyId}/experiments")
	public ResponseEntity<ApiResponse<List<CleaningPolicyExperimentView>>> listPolicyExperiments(
			@PathVariable Long policyId, @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.listPolicyExperiments(policyId, limit)));
	}

	@GetMapping("/rules")
	public ResponseEntity<ApiResponse<List<CleaningRule>>> listRules() {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.listRules()));
	}

	@PostMapping("/rules")
	public ResponseEntity<ApiResponse<CleaningRule>> createRule(@RequestBody @Valid CleaningRuleRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.createRule(request)));
	}

	@PutMapping("/rules/{ruleId}")
	public ResponseEntity<ApiResponse<CleaningRule>> updateRule(@PathVariable Long ruleId,
			@RequestBody @Valid CleaningRuleRequest request) {
		return ResponseEntity.ok(ApiResponse.success("success", policyService.updateRule(ruleId, request)));
	}

	@DeleteMapping("/rules/{ruleId}")
	public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long ruleId) {
		policyService.deleteRule(ruleId);
		return ResponseEntity.ok(ApiResponse.success("success"));
	}

}
