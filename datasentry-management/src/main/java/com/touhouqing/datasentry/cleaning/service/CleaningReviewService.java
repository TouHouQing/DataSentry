package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewBatchRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewBatchResult;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewDecisionRequest;
import com.touhouqing.datasentry.cleaning.enums.CleaningReviewStatus;
import com.touhouqing.datasentry.cleaning.mapper.CleaningBackupRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningBackupRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.util.CleaningWritebackValidator;
import com.touhouqing.datasentry.connector.pool.DBConnectionPool;
import com.touhouqing.datasentry.connector.pool.DBConnectionPoolFactory;
import com.touhouqing.datasentry.entity.Datasource;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.service.datasource.DatasourceService;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningReviewService {

	private static final int BULK_LIMIT = 5000;

	private final CleaningReviewTaskMapper reviewTaskMapper;

	private final CleaningBackupRecordMapper backupRecordMapper;

	private final CleaningJobRunMapper jobRunMapper;

	private final CleaningRecordMapper recordMapper;

	private final CleaningBackupEncryptionService encryptionService;

	private final DatasourceService datasourceService;

	private final DBConnectionPoolFactory connectionPoolFactory;

	public List<CleaningReviewTask> listReviews(String status, Long jobRunId, Long agentId) {
		LambdaQueryWrapper<CleaningReviewTask> wrapper = new LambdaQueryWrapper<CleaningReviewTask>()
			.orderByDesc(CleaningReviewTask::getCreatedTime);
		if (status != null && !status.isBlank()) {
			wrapper.eq(CleaningReviewTask::getStatus, status);
		}
		if (jobRunId != null) {
			wrapper.eq(CleaningReviewTask::getJobRunId, jobRunId);
		}
		if (agentId != null) {
			wrapper.eq(CleaningReviewTask::getAgentId, agentId);
		}
		return reviewTaskMapper.selectList(wrapper);
	}

	public CleaningReviewTask getReview(Long id) {
		return reviewTaskMapper.selectById(id);
	}

	public CleaningReviewTask approve(Long id, CleaningReviewDecisionRequest request) {
		CleaningReviewTask task = requirePendingTask(id);
		String reviewer = resolveReviewer(request.getReviewer());
		String reason = request.getReason();
		LocalDateTime now = LocalDateTime.now();
		int updated = reviewTaskMapper.updateStatusWithVersion(id, request.getVersion(),
				CleaningReviewStatus.APPROVED.name(), reviewer, reason, now);
		if (updated == 0) {
			throw new InvalidInputException("Task has been modified by others");
		}
		CleaningReviewTask locked = reviewTaskMapper.selectById(id);
		return executeWriteback(locked, reviewer, reason);
	}

	public CleaningReviewTask reject(Long id, CleaningReviewDecisionRequest request) {
		CleaningReviewTask task = requirePendingTask(id);
		String reviewer = resolveReviewer(request.getReviewer());
		String reason = request.getReason();
		LocalDateTime now = LocalDateTime.now();
		int updated = reviewTaskMapper.updateStatusWithVersion(id, request.getVersion(),
				CleaningReviewStatus.REJECTED.name(), reviewer, reason, now);
		if (updated == 0) {
			throw new InvalidInputException("Task has been modified by others");
		}
		CleaningReviewTask rejected = reviewTaskMapper.selectById(id);
		appendReviewRecord(rejected, "REJECT");
		return rejected;
	}

	public CleaningReviewBatchResult batchApprove(CleaningReviewBatchRequest request) {
		return batchHandle(request, true);
	}

	public CleaningReviewBatchResult batchReject(CleaningReviewBatchRequest request) {
		return batchHandle(request, false);
	}

	private CleaningReviewBatchResult batchHandle(CleaningReviewBatchRequest request, boolean approve) {
		List<CleaningReviewTask> tasks = resolveBatchTargets(request);
		int total = tasks.size();
		int success = 0;
		int failed = 0;
		int conflict = 0;
		int stale = 0;
		for (CleaningReviewTask task : tasks) {
			try {
				if (!CleaningReviewStatus.PENDING.name().equals(task.getStatus())) {
					stale++;
					continue;
				}
				String reviewer = resolveReviewer(request.getReviewer());
				String reason = request.getReason();
				LocalDateTime now = LocalDateTime.now();
				int updated = reviewTaskMapper.updateStatusWithVersion(task.getId(), task.getVersion(),
						approve ? CleaningReviewStatus.APPROVED.name() : CleaningReviewStatus.REJECTED.name(), reviewer,
						reason, now);
				if (updated == 0) {
					stale++;
					continue;
				}
				CleaningReviewTask locked = reviewTaskMapper.selectById(task.getId());
				if (approve) {
					CleaningReviewTask handled = executeWriteback(locked, reviewer, reason);
					if (CleaningReviewStatus.CONFLICT.name().equals(handled.getStatus())) {
						conflict++;
					}
					else if (CleaningReviewStatus.WRITTEN.name().equals(handled.getStatus())) {
						success++;
					}
					else {
						failed++;
					}
				}
				else {
					appendReviewRecord(locked, "REJECT");
					success++;
				}
			}
			catch (Exception e) {
				log.warn("Failed to handle review task {}", task.getId(), e);
				failed++;
			}
		}
		return CleaningReviewBatchResult.builder()
			.total(total)
			.success(success)
			.failed(failed)
			.conflict(conflict)
			.stale(stale)
			.build();
	}

	private CleaningReviewTask requirePendingTask(Long id) {
		CleaningReviewTask task = reviewTaskMapper.selectById(id);
		if (task == null) {
			throw new InvalidInputException("Review task not found");
		}
		if (!CleaningReviewStatus.PENDING.name().equals(task.getStatus())) {
			throw new InvalidInputException("Review task is not pending");
		}
		return task;
	}

	private List<CleaningReviewTask> resolveBatchTargets(CleaningReviewBatchRequest request) {
		if (request.getTaskIds() != null && !request.getTaskIds().isEmpty()) {
			List<Long> ids = request.getTaskIds().size() > BULK_LIMIT ? request.getTaskIds().subList(0, BULK_LIMIT)
					: request.getTaskIds();
			LambdaQueryWrapper<CleaningReviewTask> wrapper = new LambdaQueryWrapper<CleaningReviewTask>()
				.in(CleaningReviewTask::getId, ids);
			return reviewTaskMapper.selectList(wrapper);
		}
		if (request.getJobRunId() != null && "ALL_PENDING".equalsIgnoreCase(request.getFilter())) {
			LambdaQueryWrapper<CleaningReviewTask> wrapper = new LambdaQueryWrapper<CleaningReviewTask>()
				.eq(CleaningReviewTask::getJobRunId, request.getJobRunId())
				.eq(CleaningReviewTask::getStatus, CleaningReviewStatus.PENDING.name())
				.orderByAsc(CleaningReviewTask::getId);
			wrapper.last("LIMIT " + BULK_LIMIT);
			return reviewTaskMapper.selectList(wrapper);
		}
		throw new InvalidInputException("Invalid batch request");
	}

	private CleaningReviewTask executeWriteback(CleaningReviewTask task, String reviewer, String reason) {
		if (task == null) {
			throw new InvalidInputException("Review task not found");
		}
		if (encryptionService.isEncryptionEnabled() && !encryptionService.hasValidKey()) {
			updateStatus(task.getId(), CleaningReviewStatus.FAILED.name(), reviewer, "Missing backup master key");
			return reviewTaskMapper.selectById(task.getId());
		}
		Map<String, Object> beforeRow = parseJsonMap(task.getBeforeRowJson());
		Map<String, Object> writebackPayload = parseJsonMap(task.getWritebackPayloadJson());
		if (beforeRow.isEmpty() || writebackPayload.isEmpty()) {
			updateStatus(task.getId(), CleaningReviewStatus.FAILED.name(), reviewer, reason);
			return reviewTaskMapper.selectById(task.getId());
		}
		PkRef pkRef = resolvePk(task.getPkJson());
		if (pkRef == null) {
			updateStatus(task.getId(), CleaningReviewStatus.FAILED.name(), reviewer, reason);
			return reviewTaskMapper.selectById(task.getId());
		}
		Datasource datasource = datasourceService.getDatasourceById(task.getDatasourceId());
		if (datasource == null) {
			updateStatus(task.getId(), CleaningReviewStatus.FAILED.name(), reviewer, reason);
			return reviewTaskMapper.selectById(task.getId());
		}
		DBConnectionPool pool = connectionPoolFactory.getPoolByDbType(datasource.getType());
		try (Connection connection = pool.getConnection(datasourceService.getDbConfig(datasource))) {
			Map<String, CleaningWritebackValidator.ColumnMeta> columnMeta = CleaningWritebackValidator
				.loadColumnMeta(connection, task.getTableName());
			String validationError = CleaningWritebackValidator.validateValues(columnMeta, writebackPayload);
			if (validationError != null) {
				updateStatus(task.getId(), CleaningReviewStatus.FAILED.name(), reviewer, validationError);
				return reviewTaskMapper.selectById(task.getId());
			}
			if (!matchesCurrentRow(connection, task.getTableName(), pkRef, beforeRow)) {
				updateStatus(task.getId(), CleaningReviewStatus.CONFLICT.name(), reviewer, reason);
				return reviewTaskMapper.selectById(task.getId());
			}
			backupBeforeRow(task, beforeRow);
			executeUpdate(connection, task.getTableName(), writebackPayload, pkRef);
			updateStatus(task.getId(), CleaningReviewStatus.WRITTEN.name(), reviewer, reason);
			appendReviewRecord(task, task.getActionSuggested());
			return reviewTaskMapper.selectById(task.getId());
		}
		catch (Exception e) {
			log.warn("Failed to writeback review task {}", task.getId(), e);
			updateStatus(task.getId(), CleaningReviewStatus.FAILED.name(), reviewer, reason);
			return reviewTaskMapper.selectById(task.getId());
		}
	}

	private void updateStatus(Long id, String status, String reviewer, String reason) {
		reviewTaskMapper.updateStatusIfMatch(id, CleaningReviewStatus.APPROVED.name(), status, reviewer, reason,
				LocalDateTime.now());
	}

	private void backupBeforeRow(CleaningReviewTask task, Map<String, Object> beforeRow) {
		String beforeRowJson = toJsonSafe(beforeRow);
		String ciphertext = null;
		String plaintext = null;
		if (encryptionService.isEncryptionEnabled()) {
			ciphertext = encryptionService.encrypt(beforeRowJson);
		}
		else {
			plaintext = beforeRowJson;
		}
		CleaningBackupRecord record = CleaningBackupRecord.builder()
			.jobRunId(task.getJobRunId())
			.datasourceId(task.getDatasourceId())
			.tableName(task.getTableName())
			.pkJson(task.getPkJson())
			.pkHash(hashPk(task.getPkJson()))
			.encryptionProvider(encryptionService.getProviderName())
			.keyVersion(encryptionService.getKeyVersion())
			.beforeRowCiphertext(ciphertext)
			.beforeRowJson(plaintext)
			.createdTime(LocalDateTime.now())
			.build();
		backupRecordMapper.insert(record);
	}

	private boolean matchesCurrentRow(Connection connection, String tableName, PkRef pkRef,
			Map<String, Object> beforeRow) throws Exception {
		String columns = String.join(",", beforeRow.keySet());
		String sql = "SELECT " + columns + " FROM " + tableName + " WHERE " + pkRef.column() + " = ?";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setObject(1, pkRef.value());
			try (ResultSet rs = statement.executeQuery()) {
				if (!rs.next()) {
					return false;
				}
				for (String column : beforeRow.keySet()) {
					String beforeValue = beforeRow.get(column) != null ? String.valueOf(beforeRow.get(column)) : null;
					String currentValue = rs.getString(column);
					if (!Objects.equals(beforeValue, currentValue)) {
						return false;
					}
				}
				return true;
			}
		}
	}

	private void executeUpdate(Connection connection, String tableName, Map<String, Object> updateValues, PkRef pkRef)
			throws Exception {
		String setClause = updateValues.keySet().stream().map(col -> col + " = ?").collect(Collectors.joining(", "));
		String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + pkRef.column() + " = ?";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			int index = 1;
			for (Object value : updateValues.values()) {
				statement.setObject(index++, value);
			}
			statement.setObject(index, pkRef.value());
			statement.executeUpdate();
		}
	}

	private void appendReviewRecord(CleaningReviewTask task, String actionTaken) {
		if (task == null) {
			return;
		}
		String policySnapshotJson = resolvePolicySnapshot(task.getJobRunId());
		CleaningRecord record = CleaningRecord.builder()
			.agentId(task.getAgentId())
			.traceId(task.getJobRunId() != null ? String.valueOf(task.getJobRunId()) : null)
			.jobRunId(task.getJobRunId())
			.datasourceId(task.getDatasourceId())
			.tableName(task.getTableName())
			.pkJson(task.getPkJson())
			.columnName(task.getColumnName())
			.actionTaken(actionTaken)
			.policySnapshotJson(policySnapshotJson)
			.verdict(task.getVerdict() != null ? task.getVerdict() : "UNKNOWN")
			.categoriesJson(task.getCategoriesJson())
			.sanitizedPreview(task.getSanitizedPreview())
			.metricsJson(null)
			.executionTimeMs(null)
			.detectorSource(null)
			.createdTime(LocalDateTime.now())
			.build();
		recordMapper.insert(record);
	}

	private String resolvePolicySnapshot(Long jobRunId) {
		if (jobRunId == null) {
			return null;
		}
		CleaningJobRun run = jobRunMapper.selectById(jobRunId);
		if (run == null) {
			return null;
		}
		return run.getPolicySnapshotJson();
	}

	private PkRef resolvePk(String pkJson) {
		Map<String, Object> pkMap = parseJsonMap(pkJson);
		if (pkMap.isEmpty()) {
			return null;
		}
		Map.Entry<String, Object> entry = pkMap.entrySet().iterator().next();
		return new PkRef(entry.getKey(), entry.getValue());
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

	private String resolveReviewer(String reviewer) {
		return reviewer != null && !reviewer.isBlank() ? reviewer : "admin";
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

	private record PkRef(String column, Object value) {
	}

}
