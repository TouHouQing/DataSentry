package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningRollbackConflictResolveRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningRollbackConflictResolveResult;
import com.touhouqing.datasentry.cleaning.dto.CleaningRollbackCreateRequest;
import com.touhouqing.datasentry.cleaning.enums.CleaningRollbackStatus;
import com.touhouqing.datasentry.cleaning.mapper.CleaningBackupRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackConflictRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackVerifyRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningBackupRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningJob;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackConflictRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackRun;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackVerifyRecord;
import com.touhouqing.datasentry.connector.pool.DBConnectionPool;
import com.touhouqing.datasentry.connector.pool.DBConnectionPoolFactory;
import com.touhouqing.datasentry.entity.Datasource;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.service.datasource.DatasourceService;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningRollbackService {

	private static final int BATCH_SIZE = 200;

	private static final int DEFAULT_CONFLICT_RESOLVE_LIMIT = 500;

	private static final int MAX_CONFLICT_RESOLVE_LIMIT = 2000;

	private static final String CONFLICT_ACTION_MARK_RESOLVED = "MARK_RESOLVED";

	private static final String CONFLICT_ACTION_AUTO_RETRY = "AUTO_RETRY";

	private static final String CONFLICT_ACTION_ROUTE_TO_REVIEW = "ROUTE_TO_REVIEW";

	private final CleaningRollbackRunMapper rollbackRunMapper;

	private final CleaningBackupRecordMapper backupRecordMapper;

	private final CleaningJobRunMapper jobRunMapper;

	private final CleaningJobMapper jobMapper;

	private final DatasourceService datasourceService;

	private final DBConnectionPoolFactory connectionPoolFactory;

	private final CleaningBackupEncryptionService encryptionService;

	private final CleaningRollbackVerifyRecordMapper rollbackVerifyRecordMapper;

	private final CleaningRollbackConflictRecordMapper rollbackConflictRecordMapper;

	private final CleaningReviewTaskMapper reviewTaskMapper;

	private final DataSentryProperties dataSentryProperties;

	public CleaningRollbackRun createRollbackRun(Long runId) {
		return createRollbackRun(runId, null);
	}

	public CleaningRollbackRun createRollbackRun(Long runId, CleaningRollbackCreateRequest request) {
		CleaningJobRun jobRun = jobRunMapper.selectById(runId);
		if (jobRun == null) {
			throw new InvalidInputException("Job run not found");
		}
		if (encryptionService.isEncryptionEnabled() && !encryptionService.hasValidKey()) {
			throw new InvalidInputException(encryptionService.missingKeyHint());
		}
		LocalDateTime now = LocalDateTime.now();
		CleaningRollbackRun rollbackRun = CleaningRollbackRun.builder()
			.jobRunId(runId)
			.status(CleaningRollbackStatus.READY.name())
			.totalTarget(0L)
			.totalSuccess(0L)
			.totalFailed(0L)
			.verifyStatus(dataSentryProperties.getCleaning().isRollbackVerificationEnabled() ? "PENDING" : null)
			.conflictLevelSummary(null)
			.selectorJson(buildSelectorJson(request))
			.createdTime(now)
			.updatedTime(now)
			.build();
		rollbackRunMapper.insert(rollbackRun);
		return rollbackRun;
	}

	public CleaningRollbackRun getRollbackRun(Long rollbackRunId) {
		return rollbackRunMapper.selectById(rollbackRunId);
	}

	public List<CleaningRollbackConflictRecord> listConflictRecords(Long rollbackRunId, String level, Integer resolved,
			Integer limit) {
		LambdaQueryWrapper<CleaningRollbackConflictRecord> wrapper = new LambdaQueryWrapper<>();
		if (rollbackRunId != null) {
			wrapper.eq(CleaningRollbackConflictRecord::getRollbackRunId, rollbackRunId);
		}
		if (level != null && !level.isBlank()) {
			wrapper.eq(CleaningRollbackConflictRecord::getLevel, level.toUpperCase());
		}
		if (resolved != null) {
			wrapper.eq(CleaningRollbackConflictRecord::getResolved, resolved);
		}
		wrapper.orderByAsc(CleaningRollbackConflictRecord::getId);
		wrapper.last("LIMIT " + resolveConflictLimit(limit));
		return rollbackConflictRecordMapper.selectList(wrapper);
	}

	public CleaningRollbackConflictResolveResult resolveConflictRecords(
			CleaningRollbackConflictResolveRequest request) {
		List<CleaningRollbackConflictRecord> targets = resolveConflictTargets(request);
		String action = resolveConflictAction(request != null ? request.getAction() : null);
		int resolved = 0;
		int skipped = 0;
		int retried = 0;
		int routedToReview = 0;
		for (CleaningRollbackConflictRecord target : targets) {
			boolean canResolve = switch (action) {
				case CONFLICT_ACTION_AUTO_RETRY -> retryConflict(target);
				case CONFLICT_ACTION_ROUTE_TO_REVIEW -> routeConflictToReview(target);
				default -> true;
			};
			if (!canResolve) {
				skipped++;
				continue;
			}
			int updated = rollbackConflictRecordMapper.resolveIfUnresolved(target.getId());
			if (updated > 0) {
				resolved++;
				if (CONFLICT_ACTION_AUTO_RETRY.equals(action)) {
					retried++;
				}
				if (CONFLICT_ACTION_ROUTE_TO_REVIEW.equals(action)) {
					routedToReview++;
				}
			}
			else {
				skipped++;
			}
		}
		return CleaningRollbackConflictResolveResult.builder()
			.totalCandidates(targets.size())
			.resolved(resolved)
			.skipped(skipped)
			.action(action)
			.retried(retried)
			.routedToReview(routedToReview)
			.build();
	}

	public void processRun(CleaningRollbackRun run) {
		if (run == null) {
			return;
		}
		CleaningJobRun jobRun = jobRunMapper.selectById(run.getJobRunId());
		if (jobRun == null) {
			failRun(run.getId(), "Job run not found");
			return;
		}
		CleaningJob job = jobMapper.selectById(jobRun.getJobId());
		if (job == null) {
			failRun(run.getId(), "Job not found");
			return;
		}
		Datasource datasource = datasourceService.getDatasourceById(job.getDatasourceId());
		if (datasource == null) {
			failRun(run.getId(), "Datasource not found");
			return;
		}
		DBConnectionPool pool = connectionPoolFactory.getPoolByDbType(datasource.getType());
		Long checkpointId = run.getCheckpointId();
		Long totalTarget = defaultLong(run.getTotalTarget());
		Long totalSuccess = defaultLong(run.getTotalSuccess());
		Long totalFailed = defaultLong(run.getTotalFailed());
		int highConflicts = 0;
		int mediumConflicts = 0;
		int lowConflicts = 0;
		Selector selector = resolveSelector(run.getSelectorJson());
		try (Connection connection = pool.getConnection(datasourceService.getDbConfig(datasource))) {
			while (true) {
				List<CleaningBackupRecord> records = fetchBackupRecords(run.getJobRunId(), checkpointId, selector);
				if (records.isEmpty()) {
					LocalDateTime now = LocalDateTime.now();
					if (dataSentryProperties.getCleaning().isRollbackVerificationEnabled()) {
						String verifyStatus = resolveRunVerifyStatus(totalFailed, highConflicts, mediumConflicts);
						String conflictSummary = String.format("HIGH:%d,MEDIUM:%d,LOW:%d", highConflicts,
								mediumConflicts, lowConflicts);
						rollbackRunMapper.updateVerification(run.getId(), verifyStatus, conflictSummary, now);
					}
					rollbackRunMapper.updateStatus(run.getId(), CleaningRollbackStatus.SUCCEEDED.name(), now, now);
					return;
				}
				for (CleaningBackupRecord record : records) {
					totalTarget++;
					RestoreResult restoreResult = restoreRecord(connection, record);
					if (restoreResult.success()) {
						totalSuccess++;
					}
					else {
						totalFailed++;
					}
					if (dataSentryProperties.getCleaning().isRollbackVerificationEnabled()) {
						VerifyResult verifyResult = verifyRestoredRow(connection, record, restoreResult.beforeRow());
						recordVerify(run.getId(), record, verifyResult);
						if (!verifyResult.passed()) {
							int conflictCount = verifyResult.conflictColumns() != null
									? verifyResult.conflictColumns().size() : 0;
							String level = resolveConflictLevel(restoreResult.success(), conflictCount);
							recordConflict(run.getId(), record, level, verifyResult.message());
							switch (level) {
								case "HIGH" -> highConflicts++;
								case "MEDIUM" -> mediumConflicts++;
								default -> lowConflicts++;
							}
						}
					}
					checkpointId = record.getId();
				}
				rollbackRunMapper.updateProgress(run.getId(), checkpointId, totalTarget, totalSuccess, totalFailed,
						LocalDateTime.now());
			}
		}
		catch (Exception e) {
			log.warn("Failed to process rollback run {}", run.getId(), e);
			rollbackRunMapper.updateStatus(run.getId(), CleaningRollbackStatus.FAILED.name(), LocalDateTime.now(),
					LocalDateTime.now());
		}
	}

	private RestoreResult restoreRecord(Connection connection, CleaningBackupRecord record) {
		try {
			Map<String, Object> beforeRow = loadBeforeRow(record);
			if (beforeRow.isEmpty()) {
				return RestoreResult.failed(beforeRow, "beforeRow empty");
			}
			PkRef pkRef = resolvePk(record.getPkJson());
			if (pkRef == null) {
				return RestoreResult.failed(beforeRow, "pk missing");
			}
			executeUpdate(connection, record.getTableName(), beforeRow, pkRef);
			return RestoreResult.success(beforeRow);
		}
		catch (Exception e) {
			log.warn("Failed to restore backup record {}", record.getId(), e);
			return RestoreResult.failed(new LinkedHashMap<>(), e.getMessage());
		}
	}

	private VerifyResult verifyRestoredRow(Connection connection, CleaningBackupRecord record,
			Map<String, Object> expected) {
		try {
			if (expected == null || expected.isEmpty()) {
				return VerifyResult.failed("beforeRow empty", List.of());
			}
			PkRef pkRef = resolvePk(record.getPkJson());
			if (pkRef == null) {
				return VerifyResult.failed("pk missing", List.of());
			}
			Map<String, Object> actual = queryCurrentRow(connection, record.getTableName(), expected.keySet(), pkRef);
			if (actual.isEmpty()) {
				return VerifyResult.failed("row not found", List.of("ROW_MISSING"));
			}
			List<String> conflicts = new ArrayList<>();
			for (Map.Entry<String, Object> entry : expected.entrySet()) {
				Object actualValue = actual.get(entry.getKey());
				if (!isValueEqual(entry.getValue(), actualValue)) {
					conflicts.add(entry.getKey());
				}
			}
			if (conflicts.isEmpty()) {
				return VerifyResult.success();
			}
			return VerifyResult.failed("columns mismatch: " + String.join(",", conflicts), conflicts);
		}
		catch (Exception e) {
			return VerifyResult.failed("verify exception: " + e.getMessage(), List.of());
		}
	}

	private Map<String, Object> queryCurrentRow(Connection connection, String tableName, java.util.Set<String> columns,
			PkRef pkRef) throws Exception {
		String selectClause = String.join(",", columns);
		String sql = "SELECT " + selectClause + " FROM " + tableName + " WHERE " + buildPkWhereClause(pkRef)
				+ " LIMIT 1";
		Map<String, Object> row = new LinkedHashMap<>();
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bindPkValues(statement, 1, pkRef);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return row;
				}
				for (String column : columns) {
					row.put(column, resultSet.getObject(column));
				}
			}
		}
		return row;
	}

	private boolean isValueEqual(Object left, Object right) {
		if (left == null && right == null) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		return String.valueOf(left).equals(String.valueOf(right));
	}

	private void recordVerify(Long rollbackRunId, CleaningBackupRecord record, VerifyResult result) {
		rollbackVerifyRecordMapper.insert(CleaningRollbackVerifyRecord.builder()
			.rollbackRunId(rollbackRunId)
			.backupRecordId(record.getId())
			.status(result.passed() ? "PASSED" : "FAILED")
			.verifyMessage(result.message())
			.createdTime(LocalDateTime.now())
			.build());
	}

	private void recordConflict(Long rollbackRunId, CleaningBackupRecord record, String level, String reason) {
		rollbackConflictRecordMapper.insert(CleaningRollbackConflictRecord.builder()
			.rollbackRunId(rollbackRunId)
			.backupRecordId(record.getId())
			.level(level)
			.reason(reason)
			.resolved(0)
			.createdTime(LocalDateTime.now())
			.build());
	}

	private String resolveConflictLevel(boolean restoreSuccess, int conflictSize) {
		if (!restoreSuccess) {
			return "HIGH";
		}
		if (conflictSize >= 3) {
			return "HIGH";
		}
		if (conflictSize == 2) {
			return "MEDIUM";
		}
		return "LOW";
	}

	private String resolveRunVerifyStatus(Long totalFailed, int highConflicts, int mediumConflicts) {
		if (defaultLong(totalFailed) == 0L && highConflicts == 0 && mediumConflicts == 0) {
			return "PASSED";
		}
		if (highConflicts > 0) {
			return "FAILED";
		}
		return "PARTIAL";
	}

	private Map<String, Object> loadBeforeRow(CleaningBackupRecord record) {
		String json = record.getBeforeRowJson();
		if (json == null || json.isBlank()) {
			if (record.getBeforeRowCiphertext() == null || record.getBeforeRowCiphertext().isBlank()) {
				return new LinkedHashMap<>();
			}
			json = encryptionService.decrypt(record.getBeforeRowCiphertext());
		}
		return parseJsonMap(json);
	}

	private void executeUpdate(Connection connection, String tableName, Map<String, Object> updateValues, PkRef pkRef)
			throws Exception {
		String setClause = updateValues.keySet().stream().map(col -> col + " = ?").collect(Collectors.joining(", "));
		String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + buildPkWhereClause(pkRef);
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			int index = 1;
			for (Object value : updateValues.values()) {
				statement.setObject(index++, value);
			}
			bindPkValues(statement, index, pkRef);
			statement.executeUpdate();
		}
	}

	private Map<String, Object> parseJsonMap(String json) {
		if (json == null || json.isBlank()) {
			return new LinkedHashMap<>();
		}
		try {
			Map<String, Object> parsed = JsonUtil.getObjectMapper().readValue(json, Map.class);
			return parsed != null ? parsed : new LinkedHashMap<>();
		}
		catch (Exception e) {
			return new LinkedHashMap<>();
		}
	}

	private String toJsonSafe(Object value) {
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (Exception e) {
			return null;
		}
	}

	private PkRef resolvePk(String pkJson) {
		Map<String, Object> pkMap = parseJsonMap(pkJson);
		if (pkMap.isEmpty()) {
			return null;
		}
		List<Map.Entry<String, Object>> entries = pkMap.entrySet()
			.stream()
			.filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
			.sorted(Comparator.comparing(Map.Entry::getKey))
			.toList();
		if (entries.isEmpty()) {
			return null;
		}
		List<String> columns = entries.stream().map(Map.Entry::getKey).toList();
		List<Object> values = entries.stream().map(Map.Entry::getValue).toList();
		return new PkRef(columns, values);
	}

	private String buildPkWhereClause(PkRef pkRef) {
		return pkRef.columns().stream().map(column -> column + " = ?").collect(Collectors.joining(" AND "));
	}

	private void bindPkValues(PreparedStatement statement, int startIndex, PkRef pkRef) throws Exception {
		int index = startIndex;
		for (Object pkValue : pkRef.values()) {
			statement.setObject(index++, pkValue);
		}
	}

	private void failRun(Long runId, String reason) {
		log.warn("Cleaning rollback run {} failed: {}", runId, reason);
		if (dataSentryProperties.getCleaning().isRollbackVerificationEnabled()) {
			rollbackRunMapper.updateVerification(runId, "FAILED", "HIGH:0,MEDIUM:0,LOW:0", LocalDateTime.now());
		}
		rollbackRunMapper.updateStatus(runId, CleaningRollbackStatus.FAILED.name(), LocalDateTime.now(),
				LocalDateTime.now());
	}

	private Long defaultLong(Long value) {
		return value != null ? value : 0L;
	}

	private List<CleaningRollbackConflictRecord> resolveConflictTargets(
			CleaningRollbackConflictResolveRequest request) {
		if (request != null && request.getConflictIds() != null && !request.getConflictIds().isEmpty()) {
			return rollbackConflictRecordMapper.selectList(new LambdaQueryWrapper<CleaningRollbackConflictRecord>()
				.in(CleaningRollbackConflictRecord::getId, request.getConflictIds()));
		}
		if (request == null || request.getRollbackRunId() == null) {
			throw new InvalidInputException("conflictIds 或 rollbackRunId 必须至少提供一个");
		}
		LambdaQueryWrapper<CleaningRollbackConflictRecord> wrapper = new LambdaQueryWrapper<CleaningRollbackConflictRecord>()
			.eq(CleaningRollbackConflictRecord::getRollbackRunId, request.getRollbackRunId())
			.eq(CleaningRollbackConflictRecord::getResolved, 0)
			.orderByAsc(CleaningRollbackConflictRecord::getId);
		if (request.getLevel() != null && !request.getLevel().isBlank()) {
			wrapper.eq(CleaningRollbackConflictRecord::getLevel, request.getLevel().toUpperCase());
		}
		wrapper.last("LIMIT " + resolveConflictLimit(request.getLimit()));
		return rollbackConflictRecordMapper.selectList(wrapper);
	}

	private int resolveConflictLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_CONFLICT_RESOLVE_LIMIT;
		}
		return Math.min(limit, MAX_CONFLICT_RESOLVE_LIMIT);
	}

	private String resolveConflictAction(String action) {
		if (action == null || action.isBlank()) {
			return CONFLICT_ACTION_MARK_RESOLVED;
		}
		String normalized = action.trim().toUpperCase();
		if (CONFLICT_ACTION_MARK_RESOLVED.equals(normalized) || CONFLICT_ACTION_AUTO_RETRY.equals(normalized)
				|| CONFLICT_ACTION_ROUTE_TO_REVIEW.equals(normalized)) {
			return normalized;
		}
		throw new InvalidInputException("action 仅支持 MARK_RESOLVED / AUTO_RETRY / ROUTE_TO_REVIEW");
	}

	private boolean retryConflict(CleaningRollbackConflictRecord conflict) {
		ConflictContext context = resolveConflictContext(conflict);
		if (context == null || context.backupRecord() == null || context.datasource() == null) {
			return false;
		}
		try {
			DBConnectionPool pool = connectionPoolFactory.getPoolByDbType(context.datasource().getType());
			try (Connection connection = pool.getConnection(datasourceService.getDbConfig(context.datasource()))) {
				RestoreResult restoreResult = restoreRecord(connection, context.backupRecord());
				if (!restoreResult.success()) {
					return false;
				}
				VerifyResult verifyResult = verifyRestoredRow(connection, context.backupRecord(),
						restoreResult.beforeRow());
				recordVerify(conflict.getRollbackRunId(), context.backupRecord(), verifyResult);
				if (!verifyResult.passed()) {
					int conflictCount = verifyResult.conflictColumns() != null ? verifyResult.conflictColumns().size()
							: 0;
					recordConflict(conflict.getRollbackRunId(), context.backupRecord(),
							resolveConflictLevel(restoreResult.success(), conflictCount),
							"retry failed: " + verifyResult.message());
					return false;
				}
				return true;
			}
		}
		catch (Exception e) {
			log.warn("Retry rollback conflict {} failed", conflict != null ? conflict.getId() : null, e);
			return false;
		}
	}

	private boolean routeConflictToReview(CleaningRollbackConflictRecord conflict) {
		ConflictContext context = resolveConflictContext(conflict);
		if (context == null || context.backupRecord() == null || context.job() == null
				|| context.rollbackRun() == null) {
			return false;
		}
		try {
			Map<String, Object> beforeRow = loadBeforeRow(context.backupRecord());
			LocalDateTime now = LocalDateTime.now();
			String reviewPayload = toJsonSafe(Map.of("rollbackRunId", context.rollbackRun().getId(), "conflictId",
					conflict.getId(), "backupRecordId", context.backupRecord().getId()));
			reviewTaskMapper.insert(CleaningReviewTask.builder()
				.jobRunId(context.rollbackRun().getJobRunId())
				.agentId(context.job().getAgentId())
				.datasourceId(context.job().getDatasourceId())
				.tableName(context.backupRecord().getTableName())
				.pkJson(context.backupRecord().getPkJson())
				.pkHash(context.backupRecord().getPkHash())
				.columnName("ROLLBACK_CONFLICT")
				.verdict("REVIEW")
				.categoriesJson(toJsonSafe(List.of("ROLLBACK_CONFLICT")))
				.sanitizedPreview(toJsonSafe(beforeRow))
				.actionSuggested("REVIEW_ROLLBACK_CONFLICT")
				.writebackPayloadJson(reviewPayload)
				.beforeRowJson(toJsonSafe(beforeRow))
				.status("PENDING")
				.reviewReason("Rollback conflict requires manual review: " + conflict.getReason())
				.version(0)
				.createdTime(now)
				.updatedTime(now)
				.build());
			return true;
		}
		catch (Exception e) {
			log.warn("Route rollback conflict {} to review failed", conflict != null ? conflict.getId() : null, e);
			return false;
		}
	}

	private ConflictContext resolveConflictContext(CleaningRollbackConflictRecord conflict) {
		if (conflict == null || conflict.getBackupRecordId() == null || conflict.getRollbackRunId() == null) {
			return null;
		}
		CleaningBackupRecord backupRecord = backupRecordMapper.selectById(conflict.getBackupRecordId());
		if (backupRecord == null) {
			return null;
		}
		CleaningRollbackRun rollbackRun = rollbackRunMapper.selectById(conflict.getRollbackRunId());
		if (rollbackRun == null || rollbackRun.getJobRunId() == null) {
			return null;
		}
		CleaningJobRun jobRun = jobRunMapper.selectById(rollbackRun.getJobRunId());
		if (jobRun == null || jobRun.getJobId() == null) {
			return null;
		}
		CleaningJob job = jobMapper.selectById(jobRun.getJobId());
		if (job == null || job.getDatasourceId() == null) {
			return null;
		}
		Datasource datasource = datasourceService.getDatasourceById(job.getDatasourceId());
		if (datasource == null) {
			return null;
		}
		return new ConflictContext(backupRecord, rollbackRun, jobRun, job, datasource);
	}

	private List<CleaningBackupRecord> fetchBackupRecords(Long jobRunId, Long checkpointId, Selector selector) {
		LambdaQueryWrapper<CleaningBackupRecord> wrapper = new LambdaQueryWrapper<CleaningBackupRecord>()
			.eq(CleaningBackupRecord::getJobRunId, jobRunId)
			.orderByAsc(CleaningBackupRecord::getId);
		if (checkpointId != null) {
			wrapper.gt(CleaningBackupRecord::getId, checkpointId);
		}
		if (selector != null) {
			if (selector.recordIds() != null && !selector.recordIds().isEmpty()) {
				wrapper.in(CleaningBackupRecord::getId, selector.recordIds());
			}
			if (selector.startTime() != null) {
				wrapper.ge(CleaningBackupRecord::getCreatedTime, selector.startTime());
			}
			if (selector.endTime() != null) {
				wrapper.le(CleaningBackupRecord::getCreatedTime, selector.endTime());
			}
		}
		wrapper.last("LIMIT " + BATCH_SIZE);
		return backupRecordMapper.selectList(wrapper);
	}

	private String buildSelectorJson(CleaningRollbackCreateRequest request) {
		if (request == null) {
			return null;
		}
		List<Long> recordIds = mergeRecordIds(request.getBackupRecordIds(), request.getRecordIds());
		LocalDateTime startTime = request.getStartTime();
		LocalDateTime endTime = request.getEndTime();
		if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
			throw new InvalidInputException("startTime 不能晚于 endTime");
		}
		Map<String, Object> selector = new LinkedHashMap<>();
		if (!recordIds.isEmpty()) {
			selector.put("recordIds", recordIds);
		}
		if (startTime != null) {
			selector.put("startEpochSeconds", startTime.toEpochSecond(ZoneOffset.UTC));
		}
		if (endTime != null) {
			selector.put("endEpochSeconds", endTime.toEpochSecond(ZoneOffset.UTC));
		}
		if (selector.isEmpty()) {
			return null;
		}
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(selector);
		}
		catch (Exception e) {
			throw new InvalidInputException("回滚选择器格式错误");
		}
	}

	private Selector resolveSelector(String selectorJson) {
		if (selectorJson == null || selectorJson.isBlank()) {
			return null;
		}
		try {
			Map<String, Object> selector = JsonUtil.getObjectMapper().readValue(selectorJson, Map.class);
			List<Long> recordIds = mergeRecordIds(resolveRecordIds(selector.get("recordIds")),
					resolveRecordIds(selector.get("backupRecordIds")));
			LocalDateTime startTime = resolveEpochSeconds(selector.get("startEpochSeconds"));
			LocalDateTime endTime = resolveEpochSeconds(selector.get("endEpochSeconds"));
			return new Selector(recordIds, startTime, endTime);
		}
		catch (Exception e) {
			log.warn("Failed to parse rollback selector selectorJson={}", selectorJson, e);
			return null;
		}
	}

	private List<Long> mergeRecordIds(List<Long> first, List<Long> second) {
		List<Long> merged = new ArrayList<>();
		if (first != null && !first.isEmpty()) {
			merged.addAll(first);
		}
		if (second != null && !second.isEmpty()) {
			merged.addAll(second);
		}
		return merged.stream().filter(id -> id != null && id > 0).distinct().toList();
	}

	private List<Long> resolveRecordIds(Object value) {
		if (!(value instanceof List<?> rawList) || rawList.isEmpty()) {
			return List.of();
		}
		List<Long> result = new ArrayList<>();
		for (Object raw : rawList) {
			if (raw == null) {
				continue;
			}
			try {
				long resolved = Long.parseLong(String.valueOf(raw));
				if (resolved > 0) {
					result.add(resolved);
				}
			}
			catch (Exception ignored) {
			}
		}
		return result.stream().distinct().toList();
	}

	private LocalDateTime resolveEpochSeconds(Object value) {
		if (value == null) {
			return null;
		}
		try {
			long epochSeconds = Long.parseLong(String.valueOf(value));
			return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
		}
		catch (Exception e) {
			return null;
		}
	}

	private record PkRef(List<String> columns, List<Object> values) {
	}

	private record Selector(List<Long> recordIds, LocalDateTime startTime, LocalDateTime endTime) {
	}

	private record ConflictContext(CleaningBackupRecord backupRecord, CleaningRollbackRun rollbackRun,
			CleaningJobRun jobRun, CleaningJob job, Datasource datasource) {
	}

	private record RestoreResult(boolean success, Map<String, Object> beforeRow, String message) {

		private static RestoreResult success(Map<String, Object> beforeRow) {
			return new RestoreResult(true, beforeRow, null);
		}

		private static RestoreResult failed(Map<String, Object> beforeRow, String message) {
			return new RestoreResult(false, beforeRow, message);
		}

	}

	private record VerifyResult(boolean passed, String message, List<String> conflictColumns) {

		private static VerifyResult success() {
			return new VerifyResult(true, "PASSED", List.of());
		}

		private static VerifyResult failed(String message, List<String> conflictColumns) {
			return new VerifyResult(false, message, conflictColumns != null ? conflictColumns : List.of());
		}

	}

}
