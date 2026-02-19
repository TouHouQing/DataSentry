package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.detector.LlmDetector;
import com.touhouqing.datasentry.cleaning.enums.CleaningJobMode;
import com.touhouqing.datasentry.cleaning.enums.CleaningBudgetStatus;
import com.touhouqing.datasentry.cleaning.enums.CleaningCostChannel;
import com.touhouqing.datasentry.cleaning.enums.CleaningJobRunStatus;
import com.touhouqing.datasentry.cleaning.enums.CleaningReviewPolicy;
import com.touhouqing.datasentry.cleaning.enums.CleaningWritebackMode;
import com.touhouqing.datasentry.cleaning.mapper.CleaningAllowlistMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningBackupRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningAllowlist;
import com.touhouqing.datasentry.cleaning.model.CleaningBackupRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningJob;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicySnapshot;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.pipeline.CleaningPipeline;
import com.touhouqing.datasentry.cleaning.util.CleaningJsonPathProcessor;
import com.touhouqing.datasentry.cleaning.util.CleaningWritebackValidator;
import com.touhouqing.datasentry.connector.pool.DBConnectionPool;
import com.touhouqing.datasentry.connector.pool.DBConnectionPoolFactory;
import com.touhouqing.datasentry.entity.Datasource;
import com.touhouqing.datasentry.enums.DatabaseDialectEnum;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.service.datasource.DatasourceService;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningBatchProcessor {

	private static final String REVIEW_BLOCK_ON_RISK_KEY = "reviewBlockOnRisk";

	private static final long PROGRESS_FLUSH_INTERVAL_MS = 5000L;

	private final CleaningJobMapper jobMapper;

	private final CleaningJobRunMapper jobRunMapper;

	private final CleaningBackupRecordMapper backupRecordMapper;

	private final CleaningRecordMapper recordMapper;

	private final CleaningReviewTaskMapper reviewTaskMapper;

	private final CleaningPolicyResolver policyResolver;

	private final CleaningAllowlistMapper allowlistMapper;

	private final CleaningPipeline pipeline;

	private final DatasourceService datasourceService;

	private final DBConnectionPoolFactory connectionPoolFactory;

	private final CleaningBackupEncryptionService encryptionService;

	private final CleaningTokenEstimator tokenEstimator;

	private final CleaningPricingService pricingService;

	private final CleaningCostLedgerService costLedgerService;

	private final CleaningBudgetService budgetService;

	private final CleaningDlqService dlqService;

	private final CleaningNotificationService notificationService;

	private final CleaningShadowService shadowService;

	private final CleaningJsonPathProcessor jsonPathProcessor;

	private final LlmDetector llmDetector;

	private final DataSentryProperties dataSentryProperties;

	public CleaningBatchProcessor(CleaningJobMapper jobMapper, CleaningJobRunMapper jobRunMapper,
			CleaningBackupRecordMapper backupRecordMapper, CleaningRecordMapper recordMapper,
			CleaningReviewTaskMapper reviewTaskMapper, CleaningPolicyResolver policyResolver,
			CleaningAllowlistMapper allowlistMapper, CleaningPipeline pipeline, DatasourceService datasourceService,
			DBConnectionPoolFactory connectionPoolFactory, CleaningBackupEncryptionService encryptionService,
			CleaningTokenEstimator tokenEstimator, CleaningPricingService pricingService,
			CleaningCostLedgerService costLedgerService, CleaningBudgetService budgetService,
			CleaningDlqService dlqService, CleaningNotificationService notificationService,
			CleaningJsonPathProcessor jsonPathProcessor, LlmDetector llmDetector,
			DataSentryProperties dataSentryProperties) {
		this(jobMapper, jobRunMapper, backupRecordMapper, recordMapper, reviewTaskMapper, policyResolver,
				allowlistMapper, pipeline, datasourceService, connectionPoolFactory, encryptionService, tokenEstimator,
				pricingService, costLedgerService, budgetService, dlqService, notificationService, null,
				jsonPathProcessor, llmDetector, dataSentryProperties);
	}

	public void processRun(CleaningJobRun run, String leaseOwner) {
		LocalDateTime now = LocalDateTime.now();
		CleaningJob job = jobMapper.selectById(run.getJobId());
		if (job == null) {
			failRun(run.getId(), now, "Job not found");
			return;
		}
		if (job.getEnabled() == null || job.getEnabled() != 1) {
			failRun(run.getId(), now, "Job disabled");
			return;
		}
		if (!dataSentryProperties.getCleaning().isEnabled()
				|| !dataSentryProperties.getCleaning().getBatch().isEnabled()) {
			failRun(run.getId(), now, "Cleaning batch disabled");
			return;
		}
		Preflight preflight = preflight(job);
		if (!preflight.ok()) {
			failRun(run.getId(), now, preflight.message());
			return;
		}

		List<CleaningAllowlist> allowlists = Optional.ofNullable(allowlistMapper.findActive()).orElse(List.of());
		CleaningPolicySnapshot snapshot = resolvePolicySnapshot(run, job);
		int ruleCount = snapshot != null && snapshot.getRules() != null ? snapshot.getRules().size() : 0;
		Long snapshotPolicyId = snapshot != null ? snapshot.getPolicyId() : null;
		String snapshotPolicyName = snapshot != null ? snapshot.getPolicyName() : null;
		List<String> pkColumns = preflight.pkColumns();
		List<String> targetColumns = preflight.targetColumns();
		Map<String, String> jsonPathMappings = preflight.jsonPathMappings();
		Map<String, String> updateMapping = preflight.updateMapping();
		Map<String, Object> softDeleteMapping = preflight.softDeleteMapping();
		List<String> selectColumns = preflight.selectColumns();
		boolean sanitizeRequested = preflight.sanitizeRequested();
		log.info(
				"Cleaning batch start runId={} jobId={} policyId={} policyName={} rules={} targetColumns={} jsonPathMappings={} allowlists={} mode={} writebackMode={} reviewPolicy={} sanitizeRequested={}",
				run.getId(), job.getId(), snapshotPolicyId, snapshotPolicyName, ruleCount, targetColumns,
				jsonPathMappings, allowlists.size(), job.getMode(), job.getWritebackMode(), job.getReviewPolicy(),
				sanitizeRequested);
		Long totalScanned = defaultLong(run.getTotalScanned());
		Long totalFlagged = defaultLong(run.getTotalFlagged());
		Long totalWritten = defaultLong(run.getTotalWritten());
		Long totalFailed = defaultLong(run.getTotalFailed());
		BigDecimal actualCost = run.getActualCost() != null ? run.getActualCost() : BigDecimal.ZERO;
		BigDecimal estimatedCost = estimateBatchCost(job, targetColumns);
		jobRunMapper.updateBudget(run.getId(), estimatedCost, actualCost, CleaningBudgetStatus.NORMAL.name(), null,
				LocalDateTime.now());
		Map<String, String> lastPkCursor = resolveLastPkCursor(run.getCheckpointJson(), pkColumns);

		Datasource datasource = datasourceService.getDatasourceById(job.getDatasourceId());
		DBConnectionPool pool = connectionPoolFactory.getPoolByDbType(datasource.getType());

		try (Connection connection = pool.getConnection(datasourceService.getDbConfig(datasource))) {
			DatabaseDialectEnum dialect = resolveDialect(connection);
			Map<String, CleaningWritebackValidator.ColumnMeta> columnMeta = CleaningWritebackValidator
				.loadColumnMeta(connection, job.getTableName());
			long lastProgressFlushTimeMs = System.currentTimeMillis();
			while (true) {
				CleaningJobRun latestRun = jobRunMapper.selectById(run.getId());
				if (latestRun == null) {
					return;
				}
				if (!CleaningJobRunStatus.RUNNING.name().equals(latestRun.getStatus())) {
					return;
				}
				List<Map<String, String>> rows = fetchRows(connection, dialect, job, pkColumns, selectColumns,
						lastPkCursor);
				if (rows.isEmpty()) {
					jobRunMapper.updateStatus(run.getId(), CleaningJobRunStatus.SUCCEEDED.name(), LocalDateTime.now(),
							LocalDateTime.now());
					return;
				}
				log.info("Cleaning batch chunk runId={} fetchedRows={} lastPk={} batchSize={}", run.getId(),
						rows.size(), lastPkCursor, resolveBatchSize(job));
				Map<String, Map<String, Map<Long, LlmDetector.LlmDetectResult>>> precomputedL3ByPkAndColumn = precomputeChunkL3(
						run.getId(), job, snapshot, rows, pkColumns, targetColumns, jsonPathMappings);
				for (Map<String, String> row : rows) {
					CleaningJobRun latestBeforeRow = jobRunMapper.selectById(run.getId());
					if (latestBeforeRow == null) {
						return;
					}
					if (!CleaningJobRunStatus.RUNNING.name().equals(latestBeforeRow.getStatus())) {
						log.info("Cleaning batch stop runId={} reason=STATUS_CHANGED status={} lastPk={}", run.getId(),
								latestBeforeRow.getStatus(), lastPkCursor);
						return;
					}
					Map<String, String> pkValues = resolvePkValues(row, pkColumns);
					if (pkValues.size() != pkColumns.size()) {
						totalFailed++;
						continue;
					}
					String pkJson = toJsonSafe(pkValues);
					String pkValueKey = buildPkValueKey(pkValues);
					RowProcessResult rowResult = processRow(run.getId(), job, snapshot, allowlists, sanitizeRequested,
							pkJson, pkValues, row, targetColumns, updateMapping, softDeleteMapping, columnMeta,
							jsonPathMappings, precomputedL3ByPkAndColumn.get(pkValueKey), connection);
					actualCost = actualCost.add(rowResult.costAmount());
					totalScanned++;
					if (rowResult.flagged()) {
						totalFlagged++;
					}
					if (rowResult.written()) {
						totalWritten++;
					}
					if (rowResult.failed()) {
						totalFailed++;
					}
					log.info(
							"Cleaning batch row runId={} pk={} flagged={} written={} failed={} scanned={} flaggedTotal={} writtenTotal={} failedTotal={}",
							run.getId(), pkJson, rowResult.flagged(), rowResult.written(), rowResult.failed(),
							totalScanned, totalFlagged, totalWritten, totalFailed);
					lastPkCursor = pkValues;
					CleaningBudgetStatus budgetStatus = budgetService.evaluate(job, actualCost);
					if (budgetStatus == CleaningBudgetStatus.HARD_EXCEEDED) {
						LocalDateTime pauseTime = LocalDateTime.now();
						String message = "预算达到硬阈值，自动暂停";
						jobRunMapper.updateProgressWithBudget(run.getId(), buildCheckpoint(lastPkCursor), totalScanned,
								totalFlagged, totalWritten, totalFailed, actualCost, budgetStatus.name(), message,
								pauseTime,
								pauseTime.plusSeconds(dataSentryProperties.getCleaning().getBatch().getLeaseSeconds()));
						jobRunMapper.updateStatusWithoutEnd(run.getId(), CleaningJobRunStatus.PAUSED.name(), pauseTime);
						notificationService.notifyAsync("WARN", "BUDGET_HARD_EXCEEDED", message,
								Map.of("jobId", job.getId(), "jobRunId", run.getId(), "actualCost", actualCost,
										"budgetHardLimit", job.getBudgetHardLimit()));
						return;
					}
					long nowMs = System.currentTimeMillis();
					if (nowMs - lastProgressFlushTimeMs >= PROGRESS_FLUSH_INTERVAL_MS) {
						LocalDateTime progressTime = LocalDateTime.now();
						LocalDateTime leaseUntil = progressTime
							.plusSeconds(dataSentryProperties.getCleaning().getBatch().getLeaseSeconds());
						CleaningBudgetStatus status = budgetService.evaluate(job, actualCost);
						String budgetMessage = status == CleaningBudgetStatus.SOFT_EXCEEDED ? "预算达到软阈值" : null;
						jobRunMapper.updateProgressWithBudget(run.getId(), buildCheckpoint(lastPkCursor), totalScanned,
								totalFlagged, totalWritten, totalFailed, actualCost, status.name(), budgetMessage,
								progressTime, leaseUntil);
						jobRunMapper.heartbeat(run.getId(), leaseOwner, leaseUntil, progressTime);
						lastProgressFlushTimeMs = nowMs;
					}
				}
				LocalDateTime heartbeatTime = LocalDateTime.now();
				LocalDateTime leaseUntil = heartbeatTime
					.plusSeconds(dataSentryProperties.getCleaning().getBatch().getLeaseSeconds());
				CleaningBudgetStatus status = budgetService.evaluate(job, actualCost);
				String budgetMessage = status == CleaningBudgetStatus.SOFT_EXCEEDED ? "预算达到软阈值" : null;
				jobRunMapper.updateProgressWithBudget(run.getId(), buildCheckpoint(lastPkCursor), totalScanned,
						totalFlagged, totalWritten, totalFailed, actualCost, status.name(), budgetMessage,
						heartbeatTime, leaseUntil);
				jobRunMapper.heartbeat(run.getId(), leaseOwner, leaseUntil, heartbeatTime);
				log.info(
						"Cleaning batch progress runId={} scanned={} flagged={} written={} failed={} actualCost={} budgetStatus={}",
						run.getId(), totalScanned, totalFlagged, totalWritten, totalFailed, actualCost, status.name());
			}
		}
		catch (Exception e) {
			log.warn("Failed to process cleaning job run {}", run.getId(), e);
			jobRunMapper.updateStatus(run.getId(), CleaningJobRunStatus.FAILED.name(), LocalDateTime.now(),
					LocalDateTime.now());
		}
	}

	private RowProcessResult processRow(Long runId, CleaningJob job, CleaningPolicySnapshot snapshot,
			List<CleaningAllowlist> allowlists, boolean sanitizeRequested, String pkJson, Map<String, String> pkValues,
			Map<String, String> row, List<String> targetColumns, Map<String, String> updateMapping,
			Map<String, Object> softDeleteMapping, Map<String, CleaningWritebackValidator.ColumnMeta> columnMeta,
			Map<String, String> jsonPathMappings,
			Map<String, Map<Long, LlmDetector.LlmDetectResult>> precomputedL3ByColumnAndRule, Connection connection) {
		try {
			BigDecimal rowCost = BigDecimal.ZERO;
			boolean writebackEnabled = CleaningJobMode.WRITEBACK.name().equalsIgnoreCase(job.getMode());
			CleaningWritebackMode writebackMode = parseWritebackMode(job.getWritebackMode());
			CleaningReviewPolicy reviewPolicy = parseReviewPolicy(job.getReviewPolicy());
			boolean reviewBlockOnRisk = resolveReviewBlockOnRisk(job, reviewPolicy);
			Map<String, CleaningContext> contextByColumn = new LinkedHashMap<>();
			Set<String> updateColumns = new LinkedHashSet<>();
			Set<String> softDeleteTriggers = new LinkedHashSet<>();
			Set<String> reviewPendingColumns = new LinkedHashSet<>();
			boolean softDeleteReviewRequired = false;
			List<CleaningReviewTask> reviewTasks = new ArrayList<>();
			boolean flagged = false;
			for (String column : targetColumns) {
				String value = row.get(column);
				if (value == null || value.isBlank()) {
					log.info("Cleaning batch column skip runId={} pk={} column={} reason=EMPTY_VALUE", runId, pkJson,
							column);
					continue;
				}
				String sourceText = resolveSourceText(column, value, jsonPathMappings);
				if (sourceText == null || sourceText.isBlank()) {
					log.info(
							"Cleaning batch column skip runId={} pk={} column={} reason=EMPTY_SOURCE_TEXT rawLength={} jsonPath={}",
							runId, pkJson, column, value.length(), jsonPathMappings.get(column));
					continue;
				}
				long estimatedTokens = tokenEstimator.estimateTokens(sourceText);
				CleaningPricingService.Pricing pricing = pricingService
					.resolvePricing(CleaningPricingService.DEFAULT_PROVIDER, CleaningPricingService.DEFAULT_MODEL);
				BigDecimal cost = costLedgerService.recordCost(new CleaningCostLedgerService.CostEntry(job.getId(),
						runId, job.getAgentId(), String.valueOf(runId), CleaningCostChannel.BATCH, "L3",
						pricing.provider(), pricing.model(), estimatedTokens, 0L, pricing.inputPricePer1k(),
						pricing.outputPricePer1k(), pricing.currency()));
				rowCost = rowCost.add(cost);
				CleaningContext context = CleaningContext.builder()
					.agentId(job.getAgentId())
					.jobRunId(runId)
					.datasourceId(job.getDatasourceId())
					.tableName(job.getTableName())
					.pkJson(pkJson)
					.columnName(column)
					.traceId(String.valueOf(runId))
					.originalText(sourceText)
					.policySnapshot(snapshot)
					.build();
				context.getMetadata().put("allowlists", allowlists);
				context.getMetadata().put("skipAudit", true);
				Map<Long, LlmDetector.LlmDetectResult> precomputedL3ByRule = precomputedL3ByColumnAndRule != null
						? precomputedL3ByColumnAndRule.get(column) : null;
				if (precomputedL3ByRule != null && !precomputedL3ByRule.isEmpty()) {
					context.getMetadata().put("precomputedL3Results", precomputedL3ByRule);
				}
				context.getMetrics().put("startTimeMs", System.currentTimeMillis());
				CleaningContext result = pipeline.execute(context, sanitizeRequested);
				CleaningShadowService.ShadowCompareOutcome shadowCompare = shadowService.compareAndRecordIfEnabled(
						result, snapshot,
						() -> pipeline.execute(buildShadowContext(result, snapshot), sanitizeRequested));
				if (shadowCompare.compared()) {
					result.getMetadata().put("shadowDiffJson", shadowCompare.diffJson());
				}
				contextByColumn.put(column, result);
				if (result.getVerdict() != null && result.getVerdict().name() != null
						&& !"ALLOW".equals(result.getVerdict().name())) {
					flagged = true;
				}
				boolean updateCandidate = writebackEnabled && writebackMode == CleaningWritebackMode.UPDATE
						&& result.getVerdict() != null && result.getVerdict().name().equals("REDACTED")
						&& result.getSanitizedText() != null
						&& isSanitizedChanged(column, value, result.getSanitizedText(), jsonPathMappings);
				boolean softDeleteCandidate = writebackEnabled && writebackMode == CleaningWritebackMode.SOFT_DELETE
						&& result.getVerdict() != null && (result.getVerdict().name().equals("BLOCK")
								|| result.getVerdict().name().equals("REDACTED"));
				boolean hardDeleteCandidate = writebackEnabled && writebackMode == CleaningWritebackMode.HARD_DELETE
						&& result.getVerdict() != null && (result.getVerdict().name().equals("BLOCK")
								|| result.getVerdict().name().equals("REDACTED"));
				String verdict = result.getVerdict() != null ? result.getVerdict().name() : null;
				boolean reviewRequired = isReviewRequired(reviewPolicy, reviewBlockOnRisk, verdict);
				boolean blockReviewCandidate = reviewRequired && "BLOCK".equals(verdict);
				boolean reviewOnlyCandidate = reviewRequired && verdict != null && !"ALLOW".equals(verdict)
						&& !updateCandidate && !softDeleteCandidate && !hardDeleteCandidate && !blockReviewCandidate;
				if (reviewRequired && (updateCandidate || softDeleteCandidate || hardDeleteCandidate
						|| blockReviewCandidate || reviewOnlyCandidate)) {
					reviewPendingColumns.add(column);
					if (softDeleteCandidate || hardDeleteCandidate) {
						softDeleteReviewRequired = true;
					}
					CleaningReviewTask task = buildReviewTask(runId, job, pkJson, column, result, updateCandidate,
							softDeleteCandidate, hardDeleteCandidate, blockReviewCandidate, reviewOnlyCandidate,
							updateMapping, softDeleteMapping, row);
					if (task != null) {
						reviewTasks.add(task);
					}
					continue;
				}
				if (updateCandidate) {
					updateColumns.add(column);
				}
				if (softDeleteCandidate) {
					softDeleteTriggers.add(column);
				}
				if (hardDeleteCandidate) {
					softDeleteTriggers.add(column);
				}
				log.info(
						"Cleaning batch column result runId={} pk={} column={} verdict={} findings={} categories={} updateCandidate={} softDeleteCandidate={} hardDeleteCandidate={} reviewRequired={} sourceLength={} jsonPath={}",
						runId, pkJson, column, result.getVerdict(),
						result.getFindings() != null ? result.getFindings().size() : 0,
						resolveCategories(result.getFindings()), updateCandidate, softDeleteCandidate,
						hardDeleteCandidate, reviewRequired, sourceText.length(), jsonPathMappings.get(column));
			}

			boolean written = false;
			boolean failed = false;
			Set<String> updateAppliedColumns = new LinkedHashSet<>();
			if (writebackEnabled) {
				try {
					if (writebackMode == CleaningWritebackMode.UPDATE && !updateColumns.isEmpty()) {
						Map<String, Object> updateValues = new LinkedHashMap<>();
						for (String column : updateColumns) {
							CleaningContext result = contextByColumn.get(column);
							if (result != null && result.getSanitizedText() != null
									&& !Objects.equals(result.getSanitizedText(), row.get(column))) {
								String targetColumn = updateMapping.getOrDefault(column, column);
								Object sanitizedValue = resolveSanitizedWriteValue(column, row.get(column),
										result.getSanitizedText(), jsonPathMappings);
								updateValues.put(targetColumn, sanitizedValue);
								updateAppliedColumns.add(column);
							}
						}
						if (!updateValues.isEmpty()) {
							backupAndWrite(runId, job, pkJson, pkValues, row, updateValues, columnMeta, connection);
							written = true;
						}
					}
					else if (writebackMode == CleaningWritebackMode.SOFT_DELETE && !softDeleteTriggers.isEmpty()
							&& !softDeleteReviewRequired) {
						if (!softDeleteMapping.isEmpty()) {
							backupAndWrite(runId, job, pkJson, pkValues, row, softDeleteMapping, columnMeta,
									connection);
							written = true;
						}
					}
					else if (writebackMode == CleaningWritebackMode.HARD_DELETE && !softDeleteTriggers.isEmpty()
							&& !softDeleteReviewRequired) {
						backupAndHardDelete(runId, job, pkJson, pkValues, row, connection);
						written = true;
					}
				}
				catch (Exception e) {
					log.warn("Failed to writeback for job {} pk {}", job.getId(), pkJson, e);
					failed = true;
				}
			}

			for (CleaningReviewTask task : reviewTasks) {
				reviewTaskMapper.insert(task);
			}
			for (Map.Entry<String, CleaningContext> entry : contextByColumn.entrySet()) {
				String column = entry.getKey();
				CleaningContext context = entry.getValue();
				String actionTaken = "NONE";
				if (reviewPendingColumns.contains(column)) {
					actionTaken = "REVIEW_PENDING";
				}
				if (writebackMode == CleaningWritebackMode.UPDATE && updateAppliedColumns.contains(column) && written) {
					actionTaken = "UPDATE";
				}
				if (writebackMode == CleaningWritebackMode.SOFT_DELETE && softDeleteTriggers.contains(column)
						&& written) {
					actionTaken = "SOFT_DELETE";
				}
				if (writebackMode == CleaningWritebackMode.HARD_DELETE && softDeleteTriggers.contains(column)
						&& written) {
					actionTaken = "HARD_DELETE";
				}
				CleaningRecord record = CleaningRecord.builder()
					.agentId(job.getAgentId())
					.traceId(String.valueOf(runId))
					.jobRunId(runId)
					.datasourceId(job.getDatasourceId())
					.tableName(job.getTableName())
					.pkJson(pkJson)
					.columnName(column)
					.actionTaken(actionTaken)
					.policySnapshotJson(toJsonSafe(snapshot))
					.verdict(context.getVerdict() != null ? context.getVerdict().name() : null)
					.categoriesJson(toJsonSafe(resolveCategories(context.getFindings())))
					.sanitizedPreview(context.getSanitizedText())
					.metricsJson(toJsonSafe(context.getMetrics()))
					.executionTimeMs(resolveExecutionTime(context))
					.detectorSource(resolveDetectorSource(context.getFindings()))
					.shadowDiffJson(resolveShadowDiffJson(context))
					.createdTime(LocalDateTime.now())
					.build();
				recordMapper.insert(record);
			}

			return new RowProcessResult(flagged, written, failed, rowCost);
		}
		catch (Exception e) {
			log.warn("Failed to process row for job {} pk {}", job.getId(), pkJson, e);
			dlqService.push(job.getId(), runId, job.getDatasourceId(), job.getTableName(), pkJson,
					Map.of("pkValues", pkValues, "targetColumns", targetColumns), e);
			return new RowProcessResult(false, false, true, BigDecimal.ZERO);
		}
	}

	private Map<String, Map<String, Map<Long, LlmDetector.LlmDetectResult>>> precomputeChunkL3(Long runId,
			CleaningJob job, CleaningPolicySnapshot snapshot, List<Map<String, String>> rows, List<String> pkColumns,
			List<String> targetColumns, Map<String, String> jsonPathMappings) {
		if (!isBatchL3Enabled(snapshot, rows, targetColumns)) {
			return Map.of();
		}
		List<CleaningRule> llmRules = resolveEnabledLlmRules(snapshot);
		if (llmRules.isEmpty()) {
			return Map.of();
		}
		int chunkBatchSize = resolveL3BatchSize();
		Map<String, Map<String, Map<Long, LlmDetector.LlmDetectResult>>> result = new ConcurrentHashMap<>();
		List<L3BatchGroup> groups = buildL3BatchGroups(rows, pkColumns, targetColumns, jsonPathMappings, llmRules,
				chunkBatchSize);
		if (groups.isEmpty()) {
			return Map.of();
		}
		int maxConcurrency = Math.max(1, dataSentryProperties.getCleaning().getL3().getMaxBatchConcurrency());
		ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);
		try {
			List<CompletableFuture<Void>> futures = new ArrayList<>();
			for (L3BatchGroup group : groups) {
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
					LlmDetector.BatchDetectResult batchResult;
					try {
						batchResult = llmDetector.detectStructuredBatch(group.inputs(), group.prompt());
					}
					catch (Exception ex) {
						batchResult = LlmDetector.BatchDetectResult.failure(Map.of(), "L3_BATCH_CALL_FAILED",
								"L3_BATCH_CALL_FAILED");
					}
					for (L3ItemRef itemRef : group.itemRefs()) {
						LlmDetector.LlmDetectResult itemResult = batchResult.results().get(itemRef.itemId());
						if (itemResult == null) {
							String mode = batchResult.mode() != null ? batchResult.mode() : "L3_BATCH_FAILED";
							itemResult = LlmDetector.LlmDetectResult.failure(
									batchResult.errorCode() != null ? batchResult.errorCode() : "L3_BATCH_FAILED", null,
									mode);
						}
						result.computeIfAbsent(itemRef.pkValueKey(), key -> new ConcurrentHashMap<>())
							.computeIfAbsent(group.column(), key -> new ConcurrentHashMap<>())
							.put(group.ruleId(), itemResult);
					}
					log.info(
							"Cleaning L3 batch runId={} column={} ruleId={} size={} parseSuccess={} mode={} errorCode={}",
							runId, group.column(), group.ruleId(), group.inputs().size(), batchResult.parseSuccess(),
							batchResult.mode(), batchResult.errorCode());
				}, executor);
				futures.add(future);
			}
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		}
		finally {
			executor.shutdownNow();
		}
		return result;
	}

	private boolean isBatchL3Enabled(CleaningPolicySnapshot snapshot, List<Map<String, String>> rows,
			List<String> targetColumns) {
		if (snapshot == null || snapshot.getConfig() == null || !snapshot.getConfig().resolvedLlmEnabled()) {
			return false;
		}
		if (rows == null || rows.isEmpty() || targetColumns == null || targetColumns.isEmpty()) {
			return false;
		}
		return dataSentryProperties.getCleaning().getL3().isBatchEnabled();
	}

	private List<CleaningRule> resolveEnabledLlmRules(CleaningPolicySnapshot snapshot) {
		if (snapshot == null || snapshot.getRules() == null || snapshot.getRules().isEmpty()) {
			return List.of();
		}
		List<CleaningRule> llmRules = new ArrayList<>();
		for (CleaningRule rule : snapshot.getRules()) {
			if (rule == null || rule.getId() == null || rule.getRuleType() == null) {
				continue;
			}
			if (!"LLM".equalsIgnoreCase(rule.getRuleType())) {
				continue;
			}
			if (rule.getEnabled() != null && rule.getEnabled() == 0) {
				continue;
			}
			llmRules.add(rule);
		}
		return llmRules;
	}

	private int resolveL3BatchSize() {
		int configured = dataSentryProperties.getCleaning().getL3().getBatchSize();
		return Math.max(configured, 1);
	}

	private List<L3BatchGroup> buildL3BatchGroups(List<Map<String, String>> rows, List<String> pkColumns,
			List<String> targetColumns, Map<String, String> jsonPathMappings, List<CleaningRule> llmRules,
			int batchSize) {
		List<L3BatchGroup> groups = new ArrayList<>();
		for (String column : targetColumns) {
			for (CleaningRule rule : llmRules) {
				String prompt = resolveLlmRulePrompt(rule);
				List<L3ItemRef> refs = new ArrayList<>();
				for (Map<String, String> row : rows) {
					Map<String, String> pkValues = resolvePkValues(row, pkColumns);
					if (pkValues.size() != pkColumns.size()) {
						continue;
					}
					String pkValueKey = buildPkValueKey(pkValues);
					String rawValue = row.get(column);
					if (rawValue == null || rawValue.isBlank()) {
						continue;
					}
					String sourceText = resolveSourceText(column, rawValue, jsonPathMappings);
					if (sourceText == null || sourceText.isBlank()) {
						continue;
					}
					String itemId = buildBatchItemId(pkValueKey, column, rule.getId());
					refs.add(new L3ItemRef(itemId, pkValueKey, sourceText));
				}
				if (refs.isEmpty()) {
					continue;
				}
				for (int start = 0; start < refs.size(); start += batchSize) {
					int end = Math.min(start + batchSize, refs.size());
					List<L3ItemRef> window = refs.subList(start, end);
					List<LlmDetector.BatchInput> inputs = window.stream()
						.map(item -> new LlmDetector.BatchInput(item.itemId(), item.text()))
						.toList();
					groups.add(new L3BatchGroup(column, rule.getId(), prompt, inputs, new ArrayList<>(window)));
				}
			}
		}
		return groups;
	}

	private String buildBatchItemId(String pkValue, String column, Long ruleId) {
		return pkValue + "|" + column + "|" + ruleId;
	}

	private String resolveLlmRulePrompt(CleaningRule rule) {
		if (rule == null || rule.getConfigJson() == null || rule.getConfigJson().isBlank()) {
			return null;
		}
		try {
			Map<String, Object> config = JsonUtil.getObjectMapper().readValue(rule.getConfigJson(), Map.class);
			if (config.get("prompt") instanceof String configuredPrompt) {
				return configuredPrompt;
			}
		}
		catch (Exception e) {
			log.warn("Failed to parse LLM rule config: {}", rule.getId(), e);
		}
		return null;
	}

	private String resolveSourceText(String column, String rawValue, Map<String, String> jsonPathMappings) {
		String jsonPath = jsonPathMappings.get(column);
		if (jsonPath == null || jsonPath.isBlank()) {
			return rawValue;
		}
		String extracted = jsonPathProcessor.extractText(rawValue, jsonPath);
		return extracted != null ? extracted : rawValue;
	}

	private boolean isSanitizedChanged(String column, String rawValue, String sanitizedText,
			Map<String, String> jsonPathMappings) {
		String jsonPath = jsonPathMappings.get(column);
		if (jsonPath == null || jsonPath.isBlank()) {
			return !Objects.equals(sanitizedText, rawValue);
		}
		String extracted = jsonPathProcessor.extractText(rawValue, jsonPath);
		if (extracted == null) {
			return !Objects.equals(sanitizedText, rawValue);
		}
		return !Objects.equals(sanitizedText, extracted);
	}

	private Object resolveSanitizedWriteValue(String column, String rawValue, String sanitizedText,
			Map<String, String> jsonPathMappings) {
		String jsonPath = jsonPathMappings.get(column);
		if (jsonPath == null || jsonPath.isBlank()) {
			return sanitizedText;
		}
		String replaced = jsonPathProcessor.replaceText(rawValue, jsonPath, sanitizedText);
		if (replaced == null) {
			return rawValue;
		}
		return replaced;
	}

	private BigDecimal estimateBatchCost(CleaningJob job, List<String> targetColumns) {
		Integer batchSize = resolveBatchSize(job);
		long roughTokens = (long) batchSize * Math.max(1, targetColumns.size()) * 100L;
		CleaningPricingService.Pricing pricing = pricingService.resolvePricing(CleaningPricingService.DEFAULT_PROVIDER,
				CleaningPricingService.DEFAULT_MODEL);
		return pricing.inputPricePer1k()
			.multiply(BigDecimal.valueOf(roughTokens))
			.divide(BigDecimal.valueOf(1000L), 4, RoundingMode.HALF_UP);
	}

	private void backupAndWrite(Long runId, CleaningJob job, String pkJson, Map<String, String> pkValues,
			Map<String, String> row, Map<String, Object> updateValues,
			Map<String, CleaningWritebackValidator.ColumnMeta> columnMeta, Connection connection) throws Exception {
		String validationError = CleaningWritebackValidator.validateValues(columnMeta, updateValues);
		if (validationError != null) {
			throw new IllegalStateException(validationError);
		}
		Map<String, Object> beforeRow = new LinkedHashMap<>();
		for (String column : updateValues.keySet()) {
			beforeRow.put(column, row.get(column));
		}
		String beforeRowJson = toJsonSafe(beforeRow);
		String ciphertext = null;
		String plaintext = null;
		if (dataSentryProperties.getCleaning().getBackup().isEncrypt()) {
			ciphertext = encryptionService.encrypt(beforeRowJson);
		}
		else {
			plaintext = beforeRowJson;
		}
		CleaningBackupRecord record = CleaningBackupRecord.builder()
			.jobRunId(runId)
			.datasourceId(job.getDatasourceId())
			.tableName(job.getTableName())
			.pkJson(pkJson)
			.pkHash(hashPk(pkJson))
			.encryptionProvider(encryptionService.getProviderName())
			.keyVersion(encryptionService.getKeyVersion())
			.beforeRowCiphertext(ciphertext)
			.beforeRowJson(plaintext)
			.createdTime(LocalDateTime.now())
			.build();
		backupRecordMapper.insert(record);
		executeUpdate(connection, job.getTableName(), updateValues, pkValues);
	}

	private void backupAndHardDelete(Long runId, CleaningJob job, String pkJson, Map<String, String> pkValues,
			Map<String, String> row, Connection connection) throws Exception {
		String beforeRowJson = toJsonSafe(row);
		String ciphertext = null;
		String plaintext = null;
		if (dataSentryProperties.getCleaning().getBackup().isEncrypt()) {
			ciphertext = encryptionService.encrypt(beforeRowJson);
		}
		else {
			plaintext = beforeRowJson;
		}
		CleaningBackupRecord record = CleaningBackupRecord.builder()
			.jobRunId(runId)
			.datasourceId(job.getDatasourceId())
			.tableName(job.getTableName())
			.pkJson(pkJson)
			.pkHash(hashPk(pkJson))
			.encryptionProvider(encryptionService.getProviderName())
			.keyVersion(encryptionService.getKeyVersion())
			.beforeRowCiphertext(ciphertext)
			.beforeRowJson(plaintext)
			.createdTime(LocalDateTime.now())
			.build();
		backupRecordMapper.insert(record);
		executeDelete(connection, job.getTableName(), pkValues);
	}

	private void executeUpdate(Connection connection, String tableName, Map<String, Object> updateValues,
			Map<String, String> pkValues) throws Exception {
		String setClause = updateValues.keySet().stream().map(col -> col + " = ?").collect(Collectors.joining(", "));
		String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + buildPkWhereClause(pkValues.keySet());
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			int index = 1;
			for (Object value : updateValues.values()) {
				statement.setObject(index++, value);
			}
			for (String pkColumn : pkValues.keySet()) {
				statement.setObject(index++, pkValues.get(pkColumn));
			}
			statement.executeUpdate();
		}
	}

	private void executeDelete(Connection connection, String tableName, Map<String, String> pkValues) throws Exception {
		String sql = "DELETE FROM " + tableName + " WHERE " + buildPkWhereClause(pkValues.keySet());
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			int index = 1;
			for (String pkColumn : pkValues.keySet()) {
				statement.setObject(index++, pkValues.get(pkColumn));
			}
			statement.executeUpdate();
		}
	}

	private List<Map<String, String>> fetchRows(Connection connection, DatabaseDialectEnum dialect, CleaningJob job,
			List<String> pkColumns, List<String> selectColumns, Map<String, String> lastPkCursor) throws Exception {
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ").append(String.join(",", selectColumns)).append(" FROM ").append(job.getTableName());
		boolean hasWhereClause = false;
		if (job.getWhereSql() != null && !job.getWhereSql().isBlank()) {
			sql.append(" WHERE (").append(job.getWhereSql()).append(")");
			hasWhereClause = true;
		}
		boolean hasPkCursor = hasValidPkCursor(lastPkCursor, pkColumns);
		if (hasPkCursor) {
			sql.append(hasWhereClause ? " AND (" : " WHERE (");
			sql.append(buildPkCursorWhereClause(pkColumns));
			sql.append(")");
		}
		sql.append(" ORDER BY ")
			.append(pkColumns.stream().map(column -> column + " ASC").collect(Collectors.joining(", ")));
		String limitClause = resolveLimitClause(dialect);
		sql.append(limitClause);
		try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			int index = 1;
			if (hasPkCursor) {
				for (int depth = 0; depth < pkColumns.size(); depth++) {
					for (int prefix = 0; prefix < depth; prefix++) {
						statement.setObject(index++, lastPkCursor.get(pkColumns.get(prefix)));
					}
					statement.setObject(index++, lastPkCursor.get(pkColumns.get(depth)));
				}
			}
			statement.setInt(index, resolveBatchSize(job));
			List<Map<String, String>> rows = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					Map<String, String> row = new LinkedHashMap<>();
					for (String column : selectColumns) {
						row.put(column, rs.getString(column));
					}
					rows.add(row);
				}
			}
			return rows;
		}
	}

	private Preflight preflight(CleaningJob job) {
		String backupStorage = dataSentryProperties.getCleaning().getBackup().getStorage();
		if (backupStorage == null || backupStorage.isBlank()) {
			backupStorage = "METADB";
		}
		if (!"METADB".equalsIgnoreCase(backupStorage) && !"BUSINESS_DB".equalsIgnoreCase(backupStorage)) {
			return Preflight.fail("Unsupported backup storage: " + backupStorage);
		}
		CleaningWritebackMode writebackMode = parseWritebackMode(job.getWritebackMode());
		boolean writebackEnabled = CleaningJobMode.WRITEBACK.name().equalsIgnoreCase(job.getMode());
		if (writebackEnabled && dataSentryProperties.getCleaning().getBackup().isEncrypt()
				&& !encryptionService.hasValidKey()) {
			return Preflight.fail(encryptionService.missingKeyHint());
		}
		List<String> pkColumns = parseJsonArray(job.getPkColumnsJson());
		if (pkColumns.isEmpty()) {
			return Preflight.fail("Primary key columns are required");
		}
		List<String> targetColumns = parseJsonArray(job.getTargetColumnsJson());
		if (targetColumns.isEmpty()) {
			return Preflight.fail("Target columns are required");
		}
		if (!dataSentryProperties.getCleaning().getBatch().isAllowWhereSql() && job.getWhereSql() != null
				&& !job.getWhereSql().isBlank()) {
			return Preflight.fail("where_sql is disabled");
		}
		Map<String, String> updateMapping = parseUpdateMapping(job.getWritebackMappingJson(), targetColumns);
		Map<String, String> jsonPathMappings = parseJsonPathMappings(job);
		Map<String, Object> softDeleteMapping = parseSoftDeleteMapping(job.getWritebackMappingJson());
		if (writebackEnabled && writebackMode == CleaningWritebackMode.SOFT_DELETE && softDeleteMapping.isEmpty()) {
			return Preflight.fail("Soft delete mapping is required");
		}
		Set<String> selectColumns = new LinkedHashSet<>();
		selectColumns.addAll(pkColumns);
		selectColumns.addAll(targetColumns);
		if (writebackEnabled && writebackMode == CleaningWritebackMode.UPDATE) {
			selectColumns.addAll(updateMapping.values());
		}
		if (writebackEnabled && writebackMode == CleaningWritebackMode.SOFT_DELETE) {
			selectColumns.addAll(softDeleteMapping.keySet());
		}
		if (writebackEnabled && writebackMode == CleaningWritebackMode.HARD_DELETE) {
			selectColumns.addAll(pkColumns);
		}
		for (String column : selectColumns) {
			if (!isValidIdentifier(column)) {
				return Preflight.fail("Invalid column: " + column);
			}
		}
		if (!isValidIdentifier(job.getTableName()) || pkColumns.stream().anyMatch(pk -> !isValidIdentifier(pk))) {
			return Preflight.fail("Invalid table or pk column");
		}
		try {
			List<String> existingColumns = datasourceService.getTableColumns(job.getDatasourceId(), job.getTableName());
			if (writebackEnabled && writebackMode == CleaningWritebackMode.HARD_DELETE) {
				selectColumns.addAll(existingColumns);
			}
			for (String column : selectColumns) {
				if (!existingColumns.contains(column)) {
					return Preflight.fail("Column not found: " + column);
				}
			}
		}
		catch (Exception e) {
			return Preflight.fail("Failed to validate columns");
		}
		boolean sanitizeRequested = writebackMode == CleaningWritebackMode.UPDATE
				|| writebackMode == CleaningWritebackMode.SOFT_DELETE
				|| writebackMode == CleaningWritebackMode.HARD_DELETE;
		return Preflight.ok(pkColumns, targetColumns, updateMapping, softDeleteMapping, jsonPathMappings,
				new ArrayList<>(selectColumns), sanitizeRequested);
	}

	private Map<String, String> parseJsonPathMappings(CleaningJob job) {
		if (job == null || job.getTargetConfigType() == null
				|| !"JSONPATH".equalsIgnoreCase(job.getTargetConfigType())) {
			return Map.of();
		}
		if (job.getTargetConfigJson() == null || job.getTargetConfigJson().isBlank()) {
			return Map.of();
		}
		try {
			return JsonUtil.getObjectMapper().readValue(job.getTargetConfigJson(), Map.class);
		}
		catch (Exception e) {
			return Map.of();
		}
	}

	private String resolveLimitClause(DatabaseDialectEnum dialect) {
		if (dialect == DatabaseDialectEnum.ORACLE) {
			return " FETCH FIRST ? ROWS ONLY";
		}
		if (dialect == DatabaseDialectEnum.SQL_SERVER) {
			return " OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
		}
		return " LIMIT ?";
	}

	private DatabaseDialectEnum resolveDialect(Connection connection) {
		try {
			DatabaseMetaData metaData = connection.getMetaData();
			String productName = metaData.getDatabaseProductName();
			return DatabaseDialectEnum.getByCode(productName).orElse(DatabaseDialectEnum.MYSQL);
		}
		catch (Exception e) {
			return DatabaseDialectEnum.MYSQL;
		}
	}

	private CleaningWritebackMode parseWritebackMode(String mode) {
		if (mode == null || mode.isBlank()) {
			return CleaningWritebackMode.NONE;
		}
		for (CleaningWritebackMode value : CleaningWritebackMode.values()) {
			if (value.name().equalsIgnoreCase(mode)) {
				return value;
			}
		}
		return CleaningWritebackMode.NONE;
	}

	private CleaningPolicySnapshot resolvePolicySnapshot(CleaningJobRun run, CleaningJob job) {
		String snapshotJson = run.getPolicySnapshotJson();
		if (snapshotJson != null && !snapshotJson.isBlank()) {
			try {
				return JsonUtil.getObjectMapper().readValue(snapshotJson, CleaningPolicySnapshot.class);
			}
			catch (Exception e) {
				log.warn("Failed to parse policy snapshot for run {}", run.getId(), e);
			}
		}
		return policyResolver.resolveSnapshot(job.getPolicyId());
	}

	private CleaningReviewPolicy parseReviewPolicy(String policy) {
		if (policy == null || policy.isBlank()) {
			return CleaningReviewPolicy.NEVER;
		}
		for (CleaningReviewPolicy value : CleaningReviewPolicy.values()) {
			if (value.name().equalsIgnoreCase(policy)) {
				return value;
			}
		}
		return CleaningReviewPolicy.NEVER;
	}

	private boolean isReviewRequired(CleaningReviewPolicy reviewPolicy, boolean reviewBlockOnRisk, String verdict) {
		if (reviewPolicy == null || reviewPolicy == CleaningReviewPolicy.NEVER) {
			return false;
		}
		if (verdict == null) {
			return false;
		}
		if (reviewPolicy == CleaningReviewPolicy.ALWAYS) {
			return !"ALLOW".equals(verdict);
		}
		if ("REVIEW".equals(verdict)) {
			return true;
		}
		return reviewBlockOnRisk && "BLOCK".equals(verdict);
	}

	private boolean resolveReviewBlockOnRisk(CleaningJob job, CleaningReviewPolicy reviewPolicy) {
		if (reviewPolicy == CleaningReviewPolicy.ALWAYS) {
			return true;
		}
		if (job == null || job.getBackupPolicyJson() == null || job.getBackupPolicyJson().isBlank()) {
			return false;
		}
		try {
			Map<String, Object> backupPolicy = JsonUtil.getObjectMapper()
				.readValue(job.getBackupPolicyJson(), Map.class);
			Object value = backupPolicy.get(REVIEW_BLOCK_ON_RISK_KEY);
			if (value instanceof Boolean) {
				return (Boolean) value;
			}
			if (value != null) {
				return "true".equalsIgnoreCase(String.valueOf(value).trim());
			}
		}
		catch (Exception e) {
			log.warn("Failed to parse backup policy for jobId={} backupPolicyJson={}", job.getId(),
					job.getBackupPolicyJson(), e);
		}
		return false;
	}

	private CleaningReviewTask buildReviewTask(Long runId, CleaningJob job, String pkJson, String column,
			CleaningContext context, boolean updateCandidate, boolean softDeleteCandidate, boolean hardDeleteCandidate,
			boolean blockReviewCandidate, boolean reviewOnlyCandidate, Map<String, String> updateMapping,
			Map<String, Object> softDeleteMapping, Map<String, String> row) {
		Map<String, Object> payload = new LinkedHashMap<>();
		Map<String, Object> beforeRow = new LinkedHashMap<>();
		String actionSuggested = null;
		if (updateCandidate) {
			String targetColumn = updateMapping.getOrDefault(column, column);
			payload.put(targetColumn, context.getSanitizedText());
			beforeRow.put(targetColumn, row.get(targetColumn));
			actionSuggested = "UPDATE";
		}
		else if (softDeleteCandidate) {
			if (softDeleteMapping.isEmpty()) {
				return null;
			}
			payload.putAll(softDeleteMapping);
			for (String targetColumn : softDeleteMapping.keySet()) {
				beforeRow.put(targetColumn, row.get(targetColumn));
			}
			actionSuggested = "SOFT_DELETE";
		}
		else if (hardDeleteCandidate) {
			beforeRow.putAll(row);
			actionSuggested = "HARD_DELETE";
		}
		else if (blockReviewCandidate) {
			actionSuggested = "BLOCK_ONLY";
			beforeRow.put(column, row.get(column));
		}
		else if (reviewOnlyCandidate) {
			actionSuggested = "REVIEW_ONLY";
			beforeRow.put(column, row.get(column));
		}
		if (payload.isEmpty()) {
			if (!blockReviewCandidate && !reviewOnlyCandidate && !hardDeleteCandidate) {
				return null;
			}
		}
		LocalDateTime now = LocalDateTime.now();
		return CleaningReviewTask.builder()
			.jobRunId(runId)
			.agentId(job.getAgentId())
			.datasourceId(job.getDatasourceId())
			.tableName(job.getTableName())
			.pkJson(pkJson)
			.pkHash(hashPk(pkJson))
			.columnName(column)
			.verdict(context.getVerdict() != null ? context.getVerdict().name() : null)
			.categoriesJson(toJsonSafe(resolveCategories(context.getFindings())))
			.sanitizedPreview(context.getSanitizedText())
			.actionSuggested(actionSuggested)
			.writebackPayloadJson(toJsonSafe(payload))
			.beforeRowJson(toJsonSafe(beforeRow))
			.status("PENDING")
			.version(0)
			.createdTime(now)
			.updatedTime(now)
			.build();
	}

	private CleaningReviewTask buildReviewTask(Long runId, CleaningJob job, String pkJson, String column,
			CleaningContext context, boolean updateCandidate, boolean softDeleteCandidate, boolean blockReviewCandidate,
			boolean reviewOnlyCandidate, Map<String, String> updateMapping, Map<String, Object> softDeleteMapping,
			Map<String, String> row) {
		return buildReviewTask(runId, job, pkJson, column, context, updateCandidate, softDeleteCandidate, false,
				blockReviewCandidate, reviewOnlyCandidate, updateMapping, softDeleteMapping, row);
	}

	private CleaningContext buildShadowContext(CleaningContext mainResult, CleaningPolicySnapshot snapshot) {
		CleaningContext shadowContext = CleaningContext.builder()
			.agentId(mainResult.getAgentId())
			.traceId(mainResult.getTraceId())
			.originalText(mainResult.getOriginalText())
			.policySnapshot(snapshot)
			.jobRunId(mainResult.getJobRunId())
			.datasourceId(mainResult.getDatasourceId())
			.tableName(mainResult.getTableName())
			.pkJson(mainResult.getPkJson())
			.columnName(mainResult.getColumnName())
			.build();
		shadowContext.getMetadata().putAll(mainResult.getMetadata());
		shadowContext.getMetadata().put("shadowTrack", true);
		shadowContext.getMetrics().put("startTimeMs", System.currentTimeMillis());
		return shadowContext;
	}

	private String resolveShadowDiffJson(CleaningContext context) {
		if (context == null || context.getMetadata() == null) {
			return null;
		}
		Object shadowDiffJson = context.getMetadata().get("shadowDiffJson");
		if (shadowDiffJson instanceof String diffText && !diffText.isBlank()) {
			return diffText;
		}
		return null;
	}

	private Map<String, String> resolveLastPkCursor(String checkpointJson, List<String> pkColumns) {
		if (checkpointJson == null || checkpointJson.isBlank()) {
			return null;
		}
		try {
			Map<?, ?> map = JsonUtil.getObjectMapper().readValue(checkpointJson, Map.class);
			Object value = map.get("lastPk");
			if (value instanceof Map<?, ?> lastPkMap) {
				Map<String, String> cursor = new LinkedHashMap<>();
				for (String pkColumn : pkColumns) {
					Object pkValue = lastPkMap.get(pkColumn);
					if (pkValue == null) {
						return null;
					}
					cursor.put(pkColumn, String.valueOf(pkValue));
				}
				return cursor;
			}
			if (value != null && pkColumns.size() == 1) {
				return new LinkedHashMap<>(Map.of(pkColumns.get(0), String.valueOf(value)));
			}
		}
		catch (Exception ignored) {
		}
		return null;
	}

	private String buildCheckpoint(Map<String, String> lastPkCursor) {
		if (lastPkCursor == null || lastPkCursor.isEmpty()) {
			return null;
		}
		return toJsonSafe(Map.of("lastPk", lastPkCursor));
	}

	private Map<String, String> resolvePkValues(Map<String, String> row, List<String> pkColumns) {
		if (row == null || row.isEmpty() || pkColumns == null || pkColumns.isEmpty()) {
			return Map.of();
		}
		Map<String, String> pkValues = new LinkedHashMap<>();
		for (String pkColumn : pkColumns) {
			String value = row.get(pkColumn);
			if (value == null || value.isBlank()) {
				return Map.of();
			}
			pkValues.put(pkColumn, value);
		}
		return pkValues;
	}

	private boolean hasValidPkCursor(Map<String, String> lastPkCursor, List<String> pkColumns) {
		if (lastPkCursor == null || lastPkCursor.isEmpty()) {
			return false;
		}
		for (String pkColumn : pkColumns) {
			if (!lastPkCursor.containsKey(pkColumn) || lastPkCursor.get(pkColumn) == null) {
				return false;
			}
		}
		return true;
	}

	private String buildPkCursorWhereClause(List<String> pkColumns) {
		List<String> conditions = new ArrayList<>();
		for (int depth = 0; depth < pkColumns.size(); depth++) {
			StringBuilder condition = new StringBuilder();
			for (int prefix = 0; prefix < depth; prefix++) {
				if (prefix > 0) {
					condition.append(" AND ");
				}
				condition.append(pkColumns.get(prefix)).append(" = ?");
			}
			if (depth > 0) {
				condition.append(" AND ");
			}
			condition.append(pkColumns.get(depth)).append(" > ?");
			conditions.add("(" + condition + ")");
		}
		return String.join(" OR ", conditions);
	}

	private String buildPkWhereClause(Iterable<String> pkColumns) {
		List<String> conditions = new ArrayList<>();
		for (String pkColumn : pkColumns) {
			conditions.add(pkColumn + " = ?");
		}
		return String.join(" AND ", conditions);
	}

	private String buildPkValueKey(Map<String, String> pkValues) {
		if (pkValues == null || pkValues.isEmpty()) {
			return "";
		}
		return pkValues.entrySet()
			.stream()
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(Collectors.joining("|"));
	}

	private Map<String, String> parseUpdateMapping(String mappingJson, List<String> targetColumns) {
		if (mappingJson == null || mappingJson.isBlank()) {
			Map<String, String> mapping = new LinkedHashMap<>();
			for (String column : targetColumns) {
				mapping.put(column, column);
			}
			return mapping;
		}
		try {
			Map<String, Object> parsed = JsonUtil.getObjectMapper().readValue(mappingJson, Map.class);
			Map<String, String> resolved = new LinkedHashMap<>();
			for (String column : targetColumns) {
				Object target = parsed.getOrDefault(column, column);
				resolved.put(column, target != null ? String.valueOf(target) : column);
			}
			return resolved;
		}
		catch (Exception e) {
			return new LinkedHashMap<>();
		}
	}

	private Map<String, Object> parseSoftDeleteMapping(String mappingJson) {
		if (mappingJson == null || mappingJson.isBlank()) {
			return new LinkedHashMap<>();
		}
		try {
			return JsonUtil.getObjectMapper().readValue(mappingJson, Map.class);
		}
		catch (Exception e) {
			return new LinkedHashMap<>();
		}
	}

	private List<String> parseJsonArray(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return JsonUtil.getObjectMapper().readValue(json, List.class);
		}
		catch (Exception e) {
			return List.of();
		}
	}

	private boolean isValidIdentifier(String value) {
		return value != null && value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*");
	}

	private Integer resolveBatchSize(CleaningJob job) {
		if (job.getBatchSize() != null && job.getBatchSize() > 0) {
			return job.getBatchSize();
		}
		return dataSentryProperties.getCleaning().getBatch().getDefaultBatchSize();
	}

	private String toJsonSafe(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (Exception e) {
			return null;
		}
	}

	private Long resolveExecutionTime(CleaningContext context) {
		Object start = context.getMetrics().get("startTimeMs");
		if (start instanceof Long) {
			return System.currentTimeMillis() - (Long) start;
		}
		return null;
	}

	private Set<String> resolveCategories(List<Finding> findings) {
		if (findings == null) {
			return Set.of();
		}
		Set<String> categories = new LinkedHashSet<>();
		for (Finding finding : findings) {
			if (finding.getCategory() != null) {
				categories.add(finding.getCategory());
			}
		}
		return categories;
	}

	private String resolveDetectorSource(List<Finding> findings) {
		if (findings == null || findings.isEmpty()) {
			return null;
		}
		return findings.stream()
			.map(Finding::getDetectorSource)
			.filter(source -> source != null && !source.isBlank())
			.distinct()
			.collect(Collectors.joining(","));
	}

	private String hashPk(String pkJson) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(pkJson.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hash);
		}
		catch (Exception e) {
			return null;
		}
	}

	private Long defaultLong(Long value) {
		return value != null ? value : 0L;
	}

	private void failRun(Long runId, LocalDateTime now, String reason) {
		log.warn("Cleaning job run {} failed: {}", runId, reason);
		jobRunMapper.updateStatus(runId, CleaningJobRunStatus.FAILED.name(), now, now);
	}

	private record Preflight(boolean ok, String message, List<String> pkColumns, List<String> targetColumns,
			Map<String, String> updateMapping, Map<String, Object> softDeleteMapping,
			Map<String, String> jsonPathMappings, List<String> selectColumns, boolean sanitizeRequested) {

		static Preflight fail(String message) {
			return new Preflight(false, message, List.of(), List.of(), Map.of(), Map.of(), Map.of(), List.of(), false);
		}

		static Preflight ok(List<String> pkColumns, List<String> targetColumns, Map<String, String> updateMapping,
				Map<String, Object> softDeleteMapping, Map<String, String> jsonPathMappings, List<String> selectColumns,
				boolean sanitizeRequested) {
			return new Preflight(true, null, pkColumns, targetColumns, updateMapping, softDeleteMapping,
					jsonPathMappings, selectColumns, sanitizeRequested);
		}
	}

	private record RowProcessResult(boolean flagged, boolean written, boolean failed, BigDecimal costAmount) {
	}

	private record L3BatchGroup(String column, Long ruleId, String prompt, List<LlmDetector.BatchInput> inputs,
			List<L3ItemRef> itemRefs) {
	}

	private record L3ItemRef(String itemId, String pkValueKey, String text) {
	}

}
