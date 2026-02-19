import axios from 'axios';
import type { ApiResponse, PageResponse } from './common';

export interface CleaningPolicyRuleItem {
  ruleId: number;
  priority?: number;
}

export interface CleaningPolicyPublishRequest {
  publishMode?: 'FULL' | 'GRAY';
  grayRatio?: number;
  experimentName?: string;
  note?: string;
  operator?: string;
}

export interface CleaningPolicyRollbackVersionRequest {
  versionId: number;
  note?: string;
  operator?: string;
}

export interface CleaningPolicyOfflineRequest {
  note?: string;
  operator?: string;
}

export interface CleaningPolicyVersion {
  id: number;
  policyId: number;
  versionNo: number;
  status: string;
  defaultAction?: string;
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningPolicyExperimentView {
  ticketId: number;
  policyId: number;
  versionId: number;
  action: string;
  publishMode?: string;
  grayRatio?: number;
  experimentName?: string;
  note?: string;
  operator?: string;
  createdTime?: string;
}

export interface CleaningPolicyView {
  id: number;
  name: string;
  description?: string;
  enabled?: number;
  defaultAction?: string;
  configJson?: string;
  createdTime?: string;
  updatedTime?: string;
  rules?: CleaningPolicyRuleItem[];
}

export interface CleaningRule {
  id?: number;
  name: string;
  ruleType: string;
  category: string;
  severity?: number;
  enabled?: number;
  configJson?: string;
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningPolicyRequest {
  name: string;
  description?: string;
  enabled?: number;
  defaultAction?: string;
  config?: Record<string, unknown>;
}

export interface CleaningPolicyTemplateView {
  id: number;
  name: string;
  description?: string;
  category?: string;
  enabled?: number;
  defaultAction?: string;
  configJson?: string;
  rules?: CleaningPolicyRuleItem[];
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningPolicyTemplateRequest {
  name: string;
  description?: string;
  category: string;
  enabled?: number;
  defaultAction?: string;
  config?: Record<string, unknown>;
  rules?: CleaningPolicyRuleItem[];
}

export interface CleaningPolicyTemplateCloneRequest {
  name: string;
  description?: string;
  enabled?: number;
}

export interface CleaningRuleRequest {
  name: string;
  ruleType: string;
  category: string;
  severity?: number;
  enabled?: number;
  config?: Record<string, unknown>;
}

export interface CleaningReviewTask {
  id: number;
  jobRunId: number;
  agentId: number;
  datasourceId: number;
  tableName: string;
  pkJson: string;
  pkHash: string;
  columnName?: string;
  verdict?: string;
  categoriesJson?: string;
  sanitizedPreview?: string;
  actionSuggested?: string;
  writebackPayloadJson?: string;
  beforeRowJson?: string;
  status: string;
  reviewer?: string;
  reviewReason?: string;
  version: number;
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningReviewDecisionRequest {
  version: number;
  reason?: string;
  reviewer?: string;
}

export interface CleaningReviewRejudgeRequest {
  version: number;
  reason?: string;
  reviewer?: string;
  verdict?: string;
  actionSuggested?: string;
  sanitizedPreview?: string;
  writebackPayloadJson?: string;
}

export interface CleaningReviewTransferRequest {
  version: number;
  reason?: string;
  reviewer?: string;
  targetReviewer?: string;
}

export interface CleaningReviewBatchRequest {
  taskIds?: number[];
  jobRunId?: number;
  filter?: string;
  reason?: string;
  reviewer?: string;
}

export interface CleaningReviewBatchResult {
  total: number;
  success: number;
  failed: number;
  conflict: number;
  stale: number;
}

export interface CleaningReviewEscalateRequest {
  overdueHours?: number;
  limit?: number;
  reviewer?: string;
  reason?: string;
}

export interface CleaningReviewEscalateResult {
  totalCandidates: number;
  escalated: number;
  skipped: number;
  overdueHours: number;
}

export interface CleaningBinding {
  id?: number;
  agentId: number;
  bindingType?: string;
  scene?: string | null;
  policyId: number;
  enabled?: number;
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningJobCreateRequest {
  agentId: number;
  datasourceId: number;
  tableName: string;
  targetConfigType?: 'COLUMNS' | 'JSONPATH';
  targetConfig?: Record<string, unknown>;
  pkColumns: string[];
  targetColumns: string[];
  whereSql?: string;
  policyId: number;
  mode?: string;
  writebackMode?: string;
  reviewPolicy?: string;
  backupPolicy?: Record<string, unknown>;
  writebackMapping?: Record<string, unknown>;
  batchSize?: number;
  budgetEnabled?: number;
  budgetSoftLimit?: number;
  budgetHardLimit?: number;
  budgetCurrency?: string;
  onlineFailClosedEnabled?: number;
  onlineRequestTokenLimit?: number;
  enabled?: number;
}

export interface CleaningJob {
  id: number;
  agentId: number;
  datasourceId: number;
  tableName: string;
  targetConfigType?: string;
  targetConfigJson?: string;
  pkColumnsJson: string;
  targetColumnsJson: string;
  whereSql?: string;
  policyId: number;
  mode?: string;
  writebackMode?: string;
  reviewPolicy?: string;
  backupPolicyJson?: string;
  writebackMappingJson?: string;
  batchSize?: number;
  budgetEnabled?: number;
  budgetSoftLimit?: number;
  budgetHardLimit?: number;
  budgetCurrency?: string;
  onlineFailClosedEnabled?: number;
  onlineRequestTokenLimit?: number;
  enabled?: number;
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningJobRun {
  id: number;
  jobId: number;
  status: string;
  checkpointJson?: string;
  policySnapshotJson?: string;
  policyVersionId?: number;
  totalScanned?: number;
  totalFlagged?: number;
  totalWritten?: number;
  totalFailed?: number;
  estimatedCost?: number;
  actualCost?: number;
  budgetStatus?: string;
  budgetMessage?: string;
  startedTime?: string;
  endedTime?: string;
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningBudgetView {
  runId: number;
  jobId: number;
  budgetEnabled?: number;
  budgetSoftLimit?: number;
  budgetHardLimit?: number;
  budgetCurrency?: string;
  estimatedCost?: number;
  actualCost?: number;
  budgetStatus?: string;
  budgetMessage?: string;
}

export interface CleaningCostLedger {
  id: number;
  jobId?: number;
  jobRunId?: number;
  agentId?: number;
  traceId?: string;
  channel: string;
  detectorLevel?: string;
  provider: string;
  model: string;
  inputTokensEst?: number;
  outputTokensEst?: number;
  unitPriceIn?: number;
  unitPriceOut?: number;
  costAmount?: number;
  currency?: string;
  createdTime?: string;
}

export interface CleaningDlqRecord {
  id: number;
  jobId?: number;
  jobRunId?: number;
  datasourceId?: number;
  tableName?: string;
  pkJson?: string;
  payloadJson?: string;
  errorMessage?: string;
  retryCount?: number;
  nextRetryTime?: string;
  status?: string;
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningPricingSyncResult {
  success: boolean;
  sourceType?: string;
  reason?: string;
  total?: number;
  inserted?: number;
  updated?: number;
  skipped?: number;
  message?: string;
  startedTime?: string;
  finishedTime?: string;
}

export interface CleaningPriceCatalog {
  id: number;
  provider: string;
  model: string;
  version?: string;
  inputPricePer1k?: number;
  outputPricePer1k?: number;
  currency?: string;
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningMetricsView {
  totalRuns?: number;
  runningRuns?: number;
  pausedRuns?: number;
  hardExceededRuns?: number;
  totalDlq?: number;
  readyDlq?: number;
  totalCost?: number;
  onlineCost?: number;
  batchCost?: number;
  lastPricingSyncTime?: string;
  pricingSyncFailureCount?: number;
  webhookPushSuccessCount?: number;
  webhookPushFailureCount?: number;
  l2ProviderStatus?: string;
  onnxModelLoadSuccessCount?: number;
  onnxModelLoadFailureCount?: number;
  onnxInferenceSuccessCount?: number;
  onnxInferenceFailureCount?: number;
  onnxFallbackCount?: number;
  onnxInferenceAvgLatencyMs?: number;
  onnxInferenceP95LatencyMs?: number;
  onnxRuntimeVersion?: string;
  onnxModelSignature?: string;
  cloudInferenceSuccessCount?: number;
  cloudInferenceFailureCount?: number;
  cloudFallbackCount?: number;
  cloudInferenceAvgLatencyMs?: number;
  cloudInferenceP95LatencyMs?: number;
  totalShadowCompareRecords?: number;
  shadowDiffRecords?: number;
  shadowDiffRate?: number;
  totalRollbackRuns?: number;
  rollbackSucceededRuns?: number;
  rollbackFailedRuns?: number;
  totalRollbackConflicts?: number;
  rollbackSuccessRate?: number;
  rollbackConflictRate?: number;
  reviewOps?: CleaningReviewOpsView;
}

export interface CleaningReviewOpsView {
  pendingTasks?: number;
  pendingHighRiskTasks?: number;
  pendingMediumRiskTasks?: number;
  pendingLowRiskTasks?: number;
  overdueTasks?: number;
  slaHours?: number;
  avgHandleMinutes?: number;
  slaComplianceRate?: number;
}

export interface CleaningAlertView {
  level: string;
  code: string;
  message: string;
  createdTime?: string;
}

export interface CleaningOptionItem {
  code: string;
  labelZh?: string;
  description?: string;
  effect?: string;
  riskLevel?: string;
  recommendedFor?: string;
  caution?: string;
  configSchemaHint?: string;
  sampleConfig?: Record<string, unknown>;
}

export interface CleaningRuleTypeField {
  name: string;
  labelZh?: string;
  type?: string;
  required?: boolean;
  defaultValue?: unknown;
  placeholder?: string;
  help?: string;
  options?: string[];
}

export interface CleaningRuleTypeSchema {
  ruleType: string;
  title?: string;
  description?: string;
  fields?: CleaningRuleTypeField[];
}

export interface CleaningSeverityGuidanceItem {
  level?: string;
  min?: number;
  max?: number;
  labelZh?: string;
  description?: string;
}

export interface CleaningThresholdItem {
  code: string;
  labelZh?: string;
  defaultValue?: number;
  description?: string;
  recommendedRange?: string;
}

export interface CleaningOptionMetaView {
  defaultActions?: CleaningOptionItem[];
  ruleTypes?: CleaningOptionItem[];
  ruleCategories?: CleaningOptionItem[];
  reviewPolicies?: CleaningOptionItem[];
  jobModes?: CleaningOptionItem[];
  writebackModes?: CleaningOptionItem[];
  runStatuses?: CleaningOptionItem[];
  verdicts?: CleaningOptionItem[];
  targetConfigTypes?: CleaningOptionItem[];
  thresholdGuidance?: CleaningThresholdItem[];
  ruleTypeSchemas?: Record<string, CleaningRuleTypeSchema>;
  ruleTypeUiBehavior?: Record<string, Record<string, boolean>>;
  severityGuidance?: CleaningSeverityGuidanceItem[];
  riskConfirmations?: Record<string, string>;
  regexTemplates?: CleaningOptionItem[];
  jsonConfigTemplates?: Record<string, unknown>;
  fieldHelp?: Record<string, string>;
}

export interface CleaningCheckRequest {
  text: string;
  scene?: string;
  policyId?: number;
}

export interface CleaningResponse {
  verdict?: string;
  categories?: string[];
  sanitizedText?: string;
}

export interface CleaningRollbackRun {
  id: number;
  jobRunId?: number;
  status?: string;
  checkpointId?: number;
  totalTarget?: number;
  totalSuccess?: number;
  totalFailed?: number;
  verifyStatus?: string;
  conflictLevelSummary?: string;
  selectorJson?: string;
  startedTime?: string;
  endedTime?: string;
  createdTime?: string;
  updatedTime?: string;
}

export interface CleaningRollbackCreateRequest {
  backupRecordIds?: number[];
  recordIds?: number[];
  startTime?: string;
  endTime?: string;
}

export interface CleaningRollbackConflictRecord {
  id: number;
  rollbackRunId?: number;
  backupRecordId?: number;
  level?: string;
  reason?: string;
  resolved?: number;
  createdTime?: string;
}

export interface CleaningRollbackConflictResolveRequest {
  conflictIds?: number[];
  rollbackRunId?: number;
  level?: string;
  limit?: number;
  action?: 'MARK_RESOLVED' | 'AUTO_RETRY' | 'ROUTE_TO_REVIEW';
}

export interface CleaningRollbackConflictResolveResult {
  totalCandidates: number;
  resolved: number;
  skipped: number;
  action?: string;
  retried?: number;
  routedToReview?: number;
}

export interface CleaningEvidenceRollbackView {
  rollbackRun?: CleaningRollbackRun;
  totalVerifyRecords?: number;
  totalConflictRecords?: number;
  verifyRecordsTruncated?: boolean;
  conflictRecordsTruncated?: boolean;
  verifyRecords?: Array<Record<string, unknown>>;
  conflictRecords?: Array<Record<string, unknown>>;
}

export interface CleaningReviewOptimizationDisputedRule {
  category: string;
  actionSuggested: string;
  total: number;
  rejected: number;
  conflict: number;
  disputeRate: number;
}

export interface CleaningReviewOptimizationThresholdSuggestion {
  category: string;
  suggestion: string;
  reason: string;
  disputeRate: number;
}

export interface CleaningReviewOptimizationView {
  totalSamples: number;
  disputedRules: CleaningReviewOptimizationDisputedRule[];
  thresholdSuggestions: CleaningReviewOptimizationThresholdSuggestion[];
}

export interface CleaningEvidenceBundleView {
  jobRunId?: number;
  exportedTime?: string;
  jobRun?: CleaningJobRun;
  policyVersion?: CleaningPolicyVersion;
  totalAuditRecords?: number;
  totalReviewTasks?: number;
  totalShadowCompareRecords?: number;
  totalRollbackRuns?: number;
  auditRecordsTruncated?: boolean;
  reviewTasksTruncated?: boolean;
  shadowCompareRecordsTruncated?: boolean;
  rollbackRunsTruncated?: boolean;
  auditRecords?: Array<Record<string, unknown>>;
  reviewTasks?: CleaningReviewTask[];
  shadowCompareRecords?: Array<Record<string, unknown>>;
  rollbackRuns?: CleaningEvidenceRollbackView[];
}

const API_BASE_URL = '/api/datasentry/cleaning';

class CleaningService {
  async createJob(payload: CleaningJobCreateRequest): Promise<CleaningJob | null> {
    const response = await axios.post<ApiResponse<CleaningJob>>(`${API_BASE_URL}/jobs`, payload);
    return response.data.data || null;
  }

  async updateJob(jobId: number, payload: CleaningJobCreateRequest): Promise<CleaningJob | null> {
    const response = await axios.put<ApiResponse<CleaningJob>>(
      `${API_BASE_URL}/jobs/${jobId}`,
      payload,
    );
    return response.data.data || null;
  }

  async deleteJob(jobId: number): Promise<void> {
    await axios.delete<ApiResponse<void>>(`${API_BASE_URL}/jobs/${jobId}`);
  }

  async getJob(jobId: number): Promise<CleaningJob | null> {
    const response = await axios.get<ApiResponse<CleaningJob>>(`${API_BASE_URL}/jobs/${jobId}`);
    return response.data.data || null;
  }

  async listJobs(params?: {
    agentId?: number;
    datasourceId?: number;
    enabled?: number;
  }): Promise<CleaningJob[]> {
    const response = await axios.get<ApiResponse<CleaningJob[]>>(`${API_BASE_URL}/jobs`, {
      params,
    });
    return response.data.data || [];
  }

  async createRun(jobId: number): Promise<CleaningJobRun | null> {
    const response = await axios.post<ApiResponse<CleaningJobRun>>(
      `${API_BASE_URL}/jobs/${jobId}/runs`,
    );
    return response.data.data || null;
  }

  async getRun(runId: number): Promise<CleaningJobRun | null> {
    const response = await axios.get<ApiResponse<CleaningJobRun>>(
      `${API_BASE_URL}/job-runs/${runId}`,
    );
    return response.data.data || null;
  }

  async listRuns(params?: { jobId?: number; status?: string }): Promise<CleaningJobRun[]> {
    const response = await axios.get<ApiResponse<CleaningJobRun[]>>(`${API_BASE_URL}/job-runs`, {
      params,
    });
    return response.data.data || [];
  }

  async pauseRun(runId: number): Promise<CleaningJobRun | null> {
    const response = await axios.post<ApiResponse<CleaningJobRun>>(
      `${API_BASE_URL}/job-runs/${runId}/pause`,
    );
    return response.data.data || null;
  }

  async resumeRun(runId: number): Promise<CleaningJobRun | null> {
    const response = await axios.post<ApiResponse<CleaningJobRun>>(
      `${API_BASE_URL}/job-runs/${runId}/resume`,
    );
    return response.data.data || null;
  }

  async cancelRun(runId: number): Promise<CleaningJobRun | null> {
    const response = await axios.post<ApiResponse<CleaningJobRun>>(
      `${API_BASE_URL}/job-runs/${runId}/cancel`,
    );
    return response.data.data || null;
  }

  async getBudget(runId: number): Promise<CleaningBudgetView | null> {
    const response = await axios.get<ApiResponse<CleaningBudgetView>>(
      `${API_BASE_URL}/job-runs/${runId}/budget`,
    );
    return response.data.data || null;
  }

  async listCostLedger(params?: {
    jobRunId?: number;
    traceId?: string;
    channel?: string;
  }): Promise<CleaningCostLedger[]> {
    const response = await axios.get<ApiResponse<CleaningCostLedger[]>>(
      `${API_BASE_URL}/cost-ledger`,
      {
        params,
      },
    );
    return response.data.data || [];
  }

  async getEvidenceBundle(runId: number): Promise<CleaningEvidenceBundleView | null> {
    const response = await axios.get<ApiResponse<CleaningEvidenceBundleView>>(
      `${API_BASE_URL}/job-runs/${runId}/evidence-bundle`,
    );
    return response.data.data || null;
  }

  async listDlq(params?: { status?: string; jobRunId?: number }): Promise<CleaningDlqRecord[]> {
    const response = await axios.get<ApiResponse<CleaningDlqRecord[]>>(`${API_BASE_URL}/dlq`, {
      params,
    });
    return response.data.data || [];
  }

  async retryDlq(id: number): Promise<void> {
    await axios.post<ApiResponse<void>>(`${API_BASE_URL}/dlq/${id}/retry`);
  }

  async getMetricsSummary(): Promise<CleaningMetricsView | null> {
    const response = await axios.get<ApiResponse<CleaningMetricsView>>(
      `${API_BASE_URL}/metrics/summary`,
    );
    return response.data.data || null;
  }

  async listAlerts(): Promise<CleaningAlertView[]> {
    const response = await axios.get<ApiResponse<CleaningAlertView[]>>(`${API_BASE_URL}/alerts`);
    return response.data.data || [];
  }

  async getOptionMeta(): Promise<CleaningOptionMetaView | null> {
    const response = await axios.get<ApiResponse<CleaningOptionMetaView>>(
      `${API_BASE_URL}/meta/options`,
    );
    return response.data.data || null;
  }

  async getOnlineDefaultBinding(agentId: number): Promise<CleaningBinding | null> {
    const response = await axios.get<ApiResponse<CleaningBinding>>(
      `${API_BASE_URL}/bindings/online-default/${agentId}`,
    );
    return response.data.data || null;
  }

  async upsertOnlineDefaultBinding(payload: {
    agentId: number;
    policyId: number;
    enabled?: number;
  }): Promise<CleaningBinding | null> {
    const response = await axios.put<ApiResponse<CleaningBinding>>(
      `${API_BASE_URL}/bindings/online-default`,
      payload,
    );
    return response.data.data || null;
  }

  async syncPricingNow(reason = 'manual'): Promise<CleaningPricingSyncResult | null> {
    const response = await axios.post<ApiResponse<CleaningPricingSyncResult>>(
      `${API_BASE_URL}/pricing/sync`,
      null,
      { params: { reason } },
    );
    return response.data.data || null;
  }

  async getPricingCatalog(): Promise<CleaningPriceCatalog[]> {
    const response = await axios.get<ApiResponse<CleaningPriceCatalog[]>>(
      `${API_BASE_URL}/pricing/catalog`,
    );
    return response.data.data || [];
  }

  async check(
    agentId: number,
    apiKey: string,
    payload: CleaningCheckRequest,
  ): Promise<CleaningResponse | null> {
    const response = await axios.post<ApiResponse<CleaningResponse>>(
      `${API_BASE_URL}/${agentId}/check`,
      payload,
      { headers: { 'X-API-KEY': apiKey } },
    );
    return response.data.data || null;
  }

  async sanitize(
    agentId: number,
    apiKey: string,
    payload: CleaningCheckRequest,
  ): Promise<CleaningResponse | null> {
    const response = await axios.post<ApiResponse<CleaningResponse>>(
      `${API_BASE_URL}/${agentId}/sanitize`,
      payload,
      { headers: { 'X-API-KEY': apiKey } },
    );
    return response.data.data || null;
  }

  async createRollback(
    runId: number,
    payload?: CleaningRollbackCreateRequest,
  ): Promise<CleaningRollbackRun | null> {
    const response = await axios.post<ApiResponse<CleaningRollbackRun>>(
      `${API_BASE_URL}/job-runs/${runId}/rollback`,
      payload || {},
    );
    return response.data.data || null;
  }

  async getRollback(rollbackRunId: number): Promise<CleaningRollbackRun | null> {
    const response = await axios.get<ApiResponse<CleaningRollbackRun>>(
      `${API_BASE_URL}/rollbacks/${rollbackRunId}`,
    );
    return response.data.data || null;
  }

  async listRollbackConflicts(params?: {
    rollbackRunId?: number;
    level?: string;
    resolved?: number;
    limit?: number;
  }): Promise<CleaningRollbackConflictRecord[]> {
    const response = await axios.get<ApiResponse<CleaningRollbackConflictRecord[]>>(
      `${API_BASE_URL}/rollback-conflicts`,
      { params },
    );
    return response.data.data || [];
  }

  async resolveRollbackConflicts(
    payload: CleaningRollbackConflictResolveRequest,
  ): Promise<CleaningRollbackConflictResolveResult | null> {
    const response = await axios.post<ApiResponse<CleaningRollbackConflictResolveResult>>(
      `${API_BASE_URL}/rollback-conflicts/resolve`,
      payload,
    );
    return response.data.data || null;
  }

  async listPolicies(): Promise<CleaningPolicyView[]> {
    const response = await axios.get<ApiResponse<CleaningPolicyView[]>>(`${API_BASE_URL}/policies`);
    return response.data.data || [];
  }

  async createPolicy(payload: CleaningPolicyRequest): Promise<CleaningPolicyView | null> {
    const response = await axios.post<ApiResponse<CleaningPolicyView>>(
      `${API_BASE_URL}/policies`,
      payload,
    );
    return response.data.data || null;
  }

  async updatePolicy(
    policyId: number,
    payload: CleaningPolicyRequest,
  ): Promise<CleaningPolicyView | null> {
    const response = await axios.put<ApiResponse<CleaningPolicyView>>(
      `${API_BASE_URL}/policies/${policyId}`,
      payload,
    );
    return response.data.data || null;
  }

  async listPolicyTemplates(): Promise<CleaningPolicyTemplateView[]> {
    const response = await axios.get<ApiResponse<CleaningPolicyTemplateView[]>>(
      `${API_BASE_URL}/policy-templates`,
    );
    return response.data.data || [];
  }

  async createPolicyTemplate(
    payload: CleaningPolicyTemplateRequest,
  ): Promise<CleaningPolicyTemplateView | null> {
    const response = await axios.post<ApiResponse<CleaningPolicyTemplateView>>(
      `${API_BASE_URL}/policy-templates`,
      payload,
    );
    return response.data.data || null;
  }

  async updatePolicyTemplate(
    templateId: number,
    payload: CleaningPolicyTemplateRequest,
  ): Promise<CleaningPolicyTemplateView | null> {
    const response = await axios.put<ApiResponse<CleaningPolicyTemplateView>>(
      `${API_BASE_URL}/policy-templates/${templateId}`,
      payload,
    );
    return response.data.data || null;
  }

  async deletePolicyTemplate(templateId: number): Promise<void> {
    await axios.delete<ApiResponse<void>>(`${API_BASE_URL}/policy-templates/${templateId}`);
  }

  async clonePolicyTemplate(
    templateId: number,
    payload: CleaningPolicyTemplateCloneRequest,
  ): Promise<CleaningPolicyView | null> {
    const response = await axios.post<ApiResponse<CleaningPolicyView>>(
      `${API_BASE_URL}/policy-templates/${templateId}/clone`,
      payload,
    );
    return response.data.data || null;
  }

  async deletePolicy(policyId: number): Promise<void> {
    await axios.delete<ApiResponse<void>>(`${API_BASE_URL}/policies/${policyId}`);
  }

  async updatePolicyRules(policyId: number, rules: CleaningPolicyRuleItem[]): Promise<void> {
    await axios.put<ApiResponse<void>>(`${API_BASE_URL}/policies/${policyId}/rules`, { rules });
  }

  async listPolicyVersions(policyId: number): Promise<CleaningPolicyVersion[]> {
    const response = await axios.get<ApiResponse<CleaningPolicyVersion[]>>(
      `${API_BASE_URL}/policies/${policyId}/versions`,
    );
    return response.data.data || [];
  }

  async publishPolicy(
    policyId: number,
    payload?: CleaningPolicyPublishRequest,
  ): Promise<CleaningPolicyVersion | null> {
    const response = await axios.post<ApiResponse<CleaningPolicyVersion>>(
      `${API_BASE_URL}/policies/${policyId}/publish`,
      payload || {},
    );
    return response.data.data || null;
  }

  async rollbackPolicyVersion(
    policyId: number,
    payload: CleaningPolicyRollbackVersionRequest,
  ): Promise<CleaningPolicyVersion | null> {
    const response = await axios.post<ApiResponse<CleaningPolicyVersion>>(
      `${API_BASE_URL}/policies/${policyId}/rollback-version`,
      payload,
    );
    return response.data.data || null;
  }

  async offlinePolicy(policyId: number, payload?: CleaningPolicyOfflineRequest): Promise<void> {
    await axios.post<ApiResponse<void>>(
      `${API_BASE_URL}/policies/${policyId}/offline`,
      payload || {},
    );
  }

  async listPolicyExperiments(
    policyId: number,
    params?: { limit?: number },
  ): Promise<CleaningPolicyExperimentView[]> {
    const response = await axios.get<ApiResponse<CleaningPolicyExperimentView[]>>(
      `${API_BASE_URL}/policies/${policyId}/experiments`,
      { params },
    );
    return response.data.data || [];
  }

  async listRules(): Promise<CleaningRule[]> {
    const response = await axios.get<ApiResponse<CleaningRule[]>>(`${API_BASE_URL}/rules`);
    return response.data.data || [];
  }

  async createRule(payload: CleaningRuleRequest): Promise<CleaningRule | null> {
    const response = await axios.post<ApiResponse<CleaningRule>>(`${API_BASE_URL}/rules`, payload);
    return response.data.data || null;
  }

  async updateRule(ruleId: number, payload: CleaningRuleRequest): Promise<CleaningRule | null> {
    const response = await axios.put<ApiResponse<CleaningRule>>(
      `${API_BASE_URL}/rules/${ruleId}`,
      payload,
    );
    return response.data.data || null;
  }

  async deleteRule(ruleId: number): Promise<void> {
    await axios.delete<ApiResponse<void>>(`${API_BASE_URL}/rules/${ruleId}`);
  }

  async listReviews(params?: {
    status?: string;
    jobRunId?: number;
    agentId?: number;
    pageNum?: number;
    pageSize?: number;
  }): Promise<PageResponse<CleaningReviewTask[]>> {
    const response = await axios.get<PageResponse<CleaningReviewTask[]>>(
      `${API_BASE_URL}/reviews`,
      {
        params,
      },
    );
    return response.data;
  }

  async approveReview(
    taskId: number,
    payload: CleaningReviewDecisionRequest,
  ): Promise<CleaningReviewTask | null> {
    const response = await axios.post<ApiResponse<CleaningReviewTask>>(
      `${API_BASE_URL}/reviews/${taskId}/approve`,
      payload,
    );
    return response.data.data || null;
  }

  async rejectReview(
    taskId: number,
    payload: CleaningReviewDecisionRequest,
  ): Promise<CleaningReviewTask | null> {
    const response = await axios.post<ApiResponse<CleaningReviewTask>>(
      `${API_BASE_URL}/reviews/${taskId}/reject`,
      payload,
    );
    return response.data.data || null;
  }

  async rejudgeReview(
    taskId: number,
    payload: CleaningReviewRejudgeRequest,
  ): Promise<CleaningReviewTask | null> {
    const response = await axios.post<ApiResponse<CleaningReviewTask>>(
      `${API_BASE_URL}/reviews/${taskId}/rejudge`,
      payload,
    );
    return response.data.data || null;
  }

  async transferReview(
    taskId: number,
    payload: CleaningReviewTransferRequest,
  ): Promise<CleaningReviewTask | null> {
    const response = await axios.post<ApiResponse<CleaningReviewTask>>(
      `${API_BASE_URL}/reviews/${taskId}/transfer`,
      payload,
    );
    return response.data.data || null;
  }

  async listOverdueReviews(params?: {
    overdueHours?: number;
    limit?: number;
  }): Promise<CleaningReviewTask[]> {
    const response = await axios.get<ApiResponse<CleaningReviewTask[]>>(
      `${API_BASE_URL}/reviews/overdue`,
      { params },
    );
    return response.data.data || [];
  }

  async escalateOverdueReviews(
    payload?: CleaningReviewEscalateRequest,
  ): Promise<CleaningReviewEscalateResult | null> {
    const response = await axios.post<ApiResponse<CleaningReviewEscalateResult>>(
      `${API_BASE_URL}/reviews/escalate-overdue`,
      payload || {},
    );
    return response.data.data || null;
  }

  async batchApprove(
    payload: CleaningReviewBatchRequest,
  ): Promise<CleaningReviewBatchResult | null> {
    const response = await axios.post<ApiResponse<CleaningReviewBatchResult>>(
      `${API_BASE_URL}/reviews/batch-approve`,
      payload,
    );
    return response.data.data || null;
  }

  async batchReject(
    payload: CleaningReviewBatchRequest,
  ): Promise<CleaningReviewBatchResult | null> {
    const response = await axios.post<ApiResponse<CleaningReviewBatchResult>>(
      `${API_BASE_URL}/reviews/batch-reject`,
      payload,
    );
    return response.data.data || null;
  }

  async getReviewOptimizationSuggestions(params?: {
    jobRunId?: number;
    agentId?: number;
    limit?: number;
  }): Promise<CleaningReviewOptimizationView | null> {
    const response = await axios.get<ApiResponse<CleaningReviewOptimizationView>>(
      `${API_BASE_URL}/reviews/optimization-suggestions`,
      { params },
    );
    return response.data.data || null;
  }
}

export default new CleaningService();
