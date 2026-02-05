package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.enums.CleaningJobMode;
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
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.pipeline.CleaningPipeline;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningBatchProcessor {

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

	private final DataSentryProperties dataSentryProperties;

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
		String pkColumn = preflight.pkColumn();
		List<String> targetColumns = preflight.targetColumns();
		Map<String, String> updateMapping = preflight.updateMapping();
		Map<String, Object> softDeleteMapping = preflight.softDeleteMapping();
		List<String> selectColumns = preflight.selectColumns();
		boolean sanitizeRequested = preflight.sanitizeRequested();
		Long totalScanned = defaultLong(run.getTotalScanned());
		Long totalFlagged = defaultLong(run.getTotalFlagged());
		Long totalWritten = defaultLong(run.getTotalWritten());
		Long totalFailed = defaultLong(run.getTotalFailed());
		String lastPk = resolveLastPk(run.getCheckpointJson());

		Datasource datasource = datasourceService.getDatasourceById(job.getDatasourceId());
		DBConnectionPool pool = connectionPoolFactory.getPoolByDbType(datasource.getType());

		try (Connection connection = pool.getConnection(datasourceService.getDbConfig(datasource))) {
			DatabaseDialectEnum dialect = resolveDialect(connection);
			Map<String, CleaningWritebackValidator.ColumnMeta> columnMeta = CleaningWritebackValidator
				.loadColumnMeta(connection, job.getTableName());
			while (true) {
				CleaningJobRun latestRun = jobRunMapper.selectById(run.getId());
				if (latestRun == null) {
					return;
				}
				if (!CleaningJobRunStatus.RUNNING.name().equals(latestRun.getStatus())) {
					return;
				}
				List<Map<String, String>> rows = fetchRows(connection, dialect, job, pkColumn, selectColumns, lastPk);
				if (rows.isEmpty()) {
					jobRunMapper.updateStatus(run.getId(), CleaningJobRunStatus.SUCCEEDED.name(), LocalDateTime.now(),
							LocalDateTime.now());
					return;
				}
				for (Map<String, String> row : rows) {
					String pkValue = row.get(pkColumn);
					if (pkValue == null) {
						totalFailed++;
						continue;
					}
					String pkJson = toJsonSafe(Map.of(pkColumn, pkValue));
					RowProcessResult rowResult = processRow(run.getId(), job, snapshot, allowlists, sanitizeRequested,
							pkJson, pkColumn, pkValue, row, targetColumns, updateMapping, softDeleteMapping, columnMeta,
							connection);
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
					lastPk = pkValue;
				}
				LocalDateTime heartbeatTime = LocalDateTime.now();
				LocalDateTime leaseUntil = heartbeatTime
					.plusSeconds(dataSentryProperties.getCleaning().getBatch().getLeaseSeconds());
				jobRunMapper.updateProgress(run.getId(), buildCheckpoint(lastPk), totalScanned, totalFlagged,
						totalWritten, totalFailed, heartbeatTime, leaseUntil);
				jobRunMapper.heartbeat(run.getId(), leaseOwner, leaseUntil, heartbeatTime);
			}
		}
		catch (Exception e) {
			log.warn("Failed to process cleaning job run {}", run.getId(), e);
			jobRunMapper.updateStatus(run.getId(), CleaningJobRunStatus.FAILED.name(), LocalDateTime.now(),
					LocalDateTime.now());
		}
	}

	private RowProcessResult processRow(Long runId, CleaningJob job, CleaningPolicySnapshot snapshot,
			List<CleaningAllowlist> allowlists, boolean sanitizeRequested, String pkJson, String pkColumn,
			String pkValue, Map<String, String> row, List<String> targetColumns, Map<String, String> updateMapping,
			Map<String, Object> softDeleteMapping, Map<String, CleaningWritebackValidator.ColumnMeta> columnMeta,
			Connection connection) {
		try {
			boolean writebackEnabled = CleaningJobMode.WRITEBACK.name().equalsIgnoreCase(job.getMode());
			CleaningWritebackMode writebackMode = parseWritebackMode(job.getWritebackMode());
			CleaningReviewPolicy reviewPolicy = parseReviewPolicy(job.getReviewPolicy());
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
					continue;
				}
				CleaningContext context = CleaningContext.builder()
					.agentId(job.getAgentId())
					.jobRunId(runId)
					.datasourceId(job.getDatasourceId())
					.tableName(job.getTableName())
					.pkJson(pkJson)
					.columnName(column)
					.traceId(String.valueOf(runId))
					.originalText(value)
					.policySnapshot(snapshot)
					.build();
				context.getMetadata().put("allowlists", allowlists);
				context.getMetadata().put("skipAudit", true);
				context.getMetrics().put("startTimeMs", System.currentTimeMillis());
				CleaningContext result = pipeline.execute(context, sanitizeRequested);
				contextByColumn.put(column, result);
				if (result.getVerdict() != null && result.getVerdict().name() != null
						&& !"ALLOW".equals(result.getVerdict().name())) {
					flagged = true;
				}
				boolean updateCandidate = writebackEnabled && writebackMode == CleaningWritebackMode.UPDATE
						&& result.getVerdict() != null && result.getVerdict().name().equals("REDACTED")
						&& result.getSanitizedText() != null
						&& !Objects.equals(result.getSanitizedText(), row.get(column));
				boolean softDeleteCandidate = writebackEnabled && writebackMode == CleaningWritebackMode.SOFT_DELETE
						&& result.getVerdict() != null && (result.getVerdict().name().equals("BLOCK")
								|| result.getVerdict().name().equals("REDACTED"));
				boolean reviewRequired = isReviewRequired(reviewPolicy,
						result.getVerdict() != null ? result.getVerdict().name() : null);
				if (reviewRequired && (updateCandidate || softDeleteCandidate)) {
					reviewPendingColumns.add(column);
					if (softDeleteCandidate) {
						softDeleteReviewRequired = true;
					}
					CleaningReviewTask task = buildReviewTask(runId, job, pkJson, column, result, updateCandidate,
							softDeleteCandidate, updateMapping, softDeleteMapping, row);
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
								updateValues.put(targetColumn, result.getSanitizedText());
								updateAppliedColumns.add(column);
							}
						}
						if (!updateValues.isEmpty()) {
							backupAndWrite(runId, job, pkJson, pkColumn, pkValue, row, updateValues, columnMeta,
									connection);
							written = true;
						}
					}
					else if (writebackMode == CleaningWritebackMode.SOFT_DELETE && !softDeleteTriggers.isEmpty()
							&& !softDeleteReviewRequired) {
						if (!softDeleteMapping.isEmpty()) {
							backupAndWrite(runId, job, pkJson, pkColumn, pkValue, row, softDeleteMapping, columnMeta,
									connection);
							written = true;
						}
					}
				}
				catch (Exception e) {
					log.warn("Failed to writeback for job {} pk {}", job.getId(), pkValue, e);
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
					.createdTime(LocalDateTime.now())
					.build();
				recordMapper.insert(record);
			}

			return new RowProcessResult(flagged, written, failed);
		}
		catch (Exception e) {
			log.warn("Failed to process row for job {} pk {}", job.getId(), pkValue, e);
			return new RowProcessResult(false, false, true);
		}
	}

	private void backupAndWrite(Long runId, CleaningJob job, String pkJson, String pkColumn, String pkValue,
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
		executeUpdate(connection, job.getTableName(), updateValues, pkColumn, pkValue);
	}

	private void executeUpdate(Connection connection, String tableName, Map<String, Object> updateValues,
			String pkColumn, String pkValue) throws Exception {
		String setClause = updateValues.keySet().stream().map(col -> col + " = ?").collect(Collectors.joining(", "));
		String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + pkColumn + " = ?";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			int index = 1;
			for (Object value : updateValues.values()) {
				statement.setObject(index++, value);
			}
			statement.setObject(index, pkValue);
			statement.executeUpdate();
		}
	}

	private List<Map<String, String>> fetchRows(Connection connection, DatabaseDialectEnum dialect, CleaningJob job,
			String pkColumn, List<String> selectColumns, String lastPk) throws Exception {
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ")
			.append(String.join(",", selectColumns))
			.append(" FROM ")
			.append(job.getTableName())
			.append(" WHERE 1=1");
		if (job.getWhereSql() != null && !job.getWhereSql().isBlank()) {
			sql.append(" AND (").append(job.getWhereSql()).append(")");
		}
		boolean hasPk = lastPk != null && !lastPk.isBlank();
		if (hasPk) {
			sql.append(" AND ").append(pkColumn).append(" > ?");
		}
		sql.append(" ORDER BY ").append(pkColumn).append(" ASC");
		String limitClause = resolveLimitClause(dialect);
		sql.append(limitClause);
		try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			int index = 1;
			if (hasPk) {
				statement.setObject(index++, lastPk);
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
		if (!"METADB".equalsIgnoreCase(dataSentryProperties.getCleaning().getBackup().getStorage())) {
			return Preflight.fail("Only METADB backup is supported");
		}
		CleaningWritebackMode writebackMode = parseWritebackMode(job.getWritebackMode());
		boolean writebackEnabled = CleaningJobMode.WRITEBACK.name().equalsIgnoreCase(job.getMode());
		if (writebackEnabled && dataSentryProperties.getCleaning().getBackup().isEncrypt()
				&& !encryptionService.hasValidKey()) {
			return Preflight.fail("Missing backup master key");
		}
		List<String> pkColumns = parseJsonArray(job.getPkColumnsJson());
		if (pkColumns.size() != 1) {
			return Preflight.fail("Only single primary key is supported");
		}
		String pkColumn = pkColumns.get(0);
		List<String> targetColumns = parseJsonArray(job.getTargetColumnsJson());
		if (targetColumns.isEmpty()) {
			return Preflight.fail("Target columns are required");
		}
		if (!dataSentryProperties.getCleaning().getBatch().isAllowWhereSql() && job.getWhereSql() != null
				&& !job.getWhereSql().isBlank()) {
			return Preflight.fail("where_sql is disabled");
		}
		Map<String, String> updateMapping = parseUpdateMapping(job.getWritebackMappingJson(), targetColumns);
		Map<String, Object> softDeleteMapping = parseSoftDeleteMapping(job.getWritebackMappingJson());
		if (writebackEnabled && writebackMode == CleaningWritebackMode.SOFT_DELETE && softDeleteMapping.isEmpty()) {
			return Preflight.fail("Soft delete mapping is required");
		}
		Set<String> selectColumns = new LinkedHashSet<>();
		selectColumns.add(pkColumn);
		selectColumns.addAll(targetColumns);
		if (writebackEnabled && writebackMode == CleaningWritebackMode.UPDATE) {
			selectColumns.addAll(updateMapping.values());
		}
		if (writebackEnabled && writebackMode == CleaningWritebackMode.SOFT_DELETE) {
			selectColumns.addAll(softDeleteMapping.keySet());
		}
		for (String column : selectColumns) {
			if (!isValidIdentifier(column)) {
				return Preflight.fail("Invalid column: " + column);
			}
		}
		if (!isValidIdentifier(job.getTableName()) || !isValidIdentifier(pkColumn)) {
			return Preflight.fail("Invalid table or pk column");
		}
		try {
			List<String> existingColumns = datasourceService.getTableColumns(job.getDatasourceId(), job.getTableName());
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
				|| writebackMode == CleaningWritebackMode.SOFT_DELETE;
		return Preflight.ok(pkColumn, targetColumns, updateMapping, softDeleteMapping, new ArrayList<>(selectColumns),
				sanitizeRequested);
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

	private boolean isReviewRequired(CleaningReviewPolicy reviewPolicy, String verdict) {
		if (reviewPolicy == null || reviewPolicy == CleaningReviewPolicy.NEVER) {
			return false;
		}
		if (verdict == null) {
			return false;
		}
		if (reviewPolicy == CleaningReviewPolicy.ALWAYS) {
			return !"ALLOW".equals(verdict);
		}
		return "REVIEW".equals(verdict) || "BLOCK".equals(verdict);
	}

	private CleaningReviewTask buildReviewTask(Long runId, CleaningJob job, String pkJson, String column,
			CleaningContext context, boolean updateCandidate, boolean softDeleteCandidate,
			Map<String, String> updateMapping, Map<String, Object> softDeleteMapping, Map<String, String> row) {
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
		if (payload.isEmpty()) {
			return null;
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

	private String resolveLastPk(String checkpointJson) {
		if (checkpointJson == null || checkpointJson.isBlank()) {
			return null;
		}
		try {
			Map<?, ?> map = JsonUtil.getObjectMapper().readValue(checkpointJson, Map.class);
			Object value = map.get("lastPk");
			return value != null ? String.valueOf(value) : null;
		}
		catch (Exception e) {
			return null;
		}
	}

	private String buildCheckpoint(String lastPk) {
		if (lastPk == null) {
			return null;
		}
		return toJsonSafe(Map.of("lastPk", lastPk));
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

	private record Preflight(boolean ok, String message, String pkColumn, List<String> targetColumns,
			Map<String, String> updateMapping, Map<String, Object> softDeleteMapping, List<String> selectColumns,
			boolean sanitizeRequested) {

		static Preflight fail(String message) {
			return new Preflight(false, message, null, List.of(), Map.of(), Map.of(), List.of(), false);
		}

		static Preflight ok(String pkColumn, List<String> targetColumns, Map<String, String> updateMapping,
				Map<String, Object> softDeleteMapping, List<String> selectColumns, boolean sanitizeRequested) {
			return new Preflight(true, null, pkColumn, targetColumns, updateMapping, softDeleteMapping, selectColumns,
					sanitizeRequested);
		}
	}

	private record RowProcessResult(boolean flagged, boolean written, boolean failed) {
	}

}
