package com.touhouqing.datasentry.cleaning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewBatchRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewBatchResult;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewDecisionRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateResult;
import com.touhouqing.datasentry.cleaning.enums.CleaningReviewStatus;
import com.touhouqing.datasentry.cleaning.mapper.CleaningBackupRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewFeedbackRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningBackupRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewFeedbackRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.util.CleaningWritebackValidator;
import com.touhouqing.datasentry.connector.pool.DBConnectionPool;
import com.touhouqing.datasentry.connector.pool.DBConnectionPoolFactory;
import com.touhouqing.datasentry.entity.Datasource;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.service.datasource.DatasourceService;
import com.touhouqing.datasentry.util.JsonUtil;
import com.touhouqing.datasentry.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningReviewService {

	private static final int BULK_LIMIT = 5000;

	private static final int DEFAULT_PAGE_NUM = 1;

	private static final int DEFAULT_PAGE_SIZE = 20;

	private static final int MAX_PAGE_SIZE = 200;

	private static final int DEFAULT_ESCALATE_HOURS = 24;

	private static final int DEFAULT_ESCALATE_LIMIT = 200;

	private static final int MAX_ESCALATE_LIMIT = 1000;

	private static final int DEFAULT_FEEDBACK_LIMIT = 200;

	private static final int MAX_FEEDBACK_LIMIT = 1000;

	private final CleaningReviewTaskMapper reviewTaskMapper;

	private final CleaningBackupRecordMapper backupRecordMapper;

	private final CleaningReviewFeedbackRecordMapper reviewFeedbackRecordMapper;

	private final CleaningJobRunMapper jobRunMapper;

	private final CleaningRecordMapper recordMapper;

	private final CleaningBackupEncryptionService encryptionService;

	private final DatasourceService datasourceService;

	private final DBConnectionPoolFactory connectionPoolFactory;

	public PageResult<CleaningReviewTask> listReviews(String status, Long jobRunId, Long agentId, Integer pageNum,
			Integer pageSize) {
		int safePageNum = pageNum != null && pageNum > 0 ? pageNum : DEFAULT_PAGE_NUM;
		int safePageSize = pageSize != null && pageSize > 0 ? Math.min(pageSize, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
		LambdaQueryWrapper<CleaningReviewTask> countWrapper = new LambdaQueryWrapper<>();
		if (status != null && !status.isBlank()) {
			countWrapper.eq(CleaningReviewTask::getStatus, status);
		}
		if (jobRunId != null) {
			countWrapper.eq(CleaningReviewTask::getJobRunId, jobRunId);
		}
		if (agentId != null) {
			countWrapper.eq(CleaningReviewTask::getAgentId, agentId);
		}
		Long total = reviewTaskMapper.selectCount(countWrapper);
		long safeTotal = total != null ? total : 0L;
		long offset = Math.max(0L, (long) (safePageNum - 1) * safePageSize);
		LambdaQueryWrapper<CleaningReviewTask> listWrapper = new LambdaQueryWrapper<CleaningReviewTask>()
			.orderByDesc(CleaningReviewTask::getCreatedTime);
		if (status != null && !status.isBlank()) {
			listWrapper.eq(CleaningReviewTask::getStatus, status);
		}
		if (jobRunId != null) {
			listWrapper.eq(CleaningReviewTask::getJobRunId, jobRunId);
		}
		if (agentId != null) {
			listWrapper.eq(CleaningReviewTask::getAgentId, agentId);
		}
		listWrapper.last("LIMIT " + offset + "," + safePageSize);
		List<CleaningReviewTask> data = reviewTaskMapper.selectList(listWrapper);
		PageResult<CleaningReviewTask> result = new PageResult<>(data, safeTotal, safePageNum, safePageSize, 0);
		result.calculateTotalPages();
		return result;
	}

	public CleaningReviewTask getReview(Long id) {
		return reviewTaskMapper.selectById(id);
	}

	public List<CleaningReviewFeedbackRecord> listFeedbackSamples(Long jobRunId, Long agentId, Integer limit) {
		int safeLimit = resolveFeedbackLimit(limit);
		return reviewFeedbackRecordMapper.listLatest(jobRunId, agentId, safeLimit);
	}

	public List<CleaningReviewTask> listOverduePending(Integer overdueHours, Integer limit) {
		int safeHours = resolveEscalateHours(overdueHours);
		int safeLimit = resolveEscalateLimit(limit);
		LocalDateTime threshold = LocalDateTime.now().minusHours(safeHours);
		LambdaQueryWrapper<CleaningReviewTask> wrapper = new LambdaQueryWrapper<CleaningReviewTask>()
			.eq(CleaningReviewTask::getStatus, CleaningReviewStatus.PENDING.name())
			.lt(CleaningReviewTask::getCreatedTime, threshold)
			.orderByAsc(CleaningReviewTask::getCreatedTime)
			.orderByAsc(CleaningReviewTask::getId);
		wrapper.last("LIMIT " + safeLimit);
		return reviewTaskMapper.selectList(wrapper);
	}

	public boolean requiresHardDeletePermission(Long taskId) {
		if (taskId == null) {
			return false;
		}
		CleaningReviewTask task = reviewTaskMapper.selectById(taskId);
		return task != null && "HARD_DELETE".equals(task.getActionSuggested());
	}

	public boolean requiresHardDeletePermission(CleaningReviewBatchRequest request) {
		List<CleaningReviewTask> targets = resolveBatchTargets(request);
		return targets.stream().anyMatch(task -> "HARD_DELETE".equals(task.getActionSuggested()));
	}

	public CleaningReviewEscalateResult escalateOverduePending(CleaningReviewEscalateRequest request) {
		int safeHours = resolveEscalateHours(request != null ? request.getOverdueHours() : null);
		int safeLimit = resolveEscalateLimit(request != null ? request.getLimit() : null);
		String reviewer = resolveReviewer(request != null ? request.getReviewer() : null);
		String reason = resolveEscalateReason(request != null ? request.getReason() : null, safeHours);

		List<CleaningReviewTask> overdueTasks = listOverduePending(safeHours, safeLimit);
		int escalated = 0;
		int skipped = 0;
		LocalDateTime now = LocalDateTime.now();
		for (CleaningReviewTask overdueTask : overdueTasks) {
			int updated = reviewTaskMapper.markEscalatedIfPending(overdueTask.getId(), reviewer, reason, now);
			if (updated > 0) {
				escalated++;
			}
			else {
				skipped++;
			}
		}
		return CleaningReviewEscalateResult.builder()
			.totalCandidates(overdueTasks.size())
			.escalated(escalated)
			.skipped(skipped)
			.overdueHours(safeHours)
			.build();
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
		appendFeedbackRecord(rejected, reviewer, reason);
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
					appendFeedbackRecord(locked, reviewer, reason);
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
		if ("BLOCK_ONLY".equals(task.getActionSuggested()) || "REVIEW_ONLY".equals(task.getActionSuggested())) {
			return completeApprovedTask(task, CleaningReviewStatus.WRITTEN.name(), reviewer, reason, true,
					task.getActionSuggested());
		}
		if (encryptionService.isEncryptionEnabled() && !encryptionService.hasValidKey()) {
			return completeApprovedTask(task, CleaningReviewStatus.FAILED.name(), reviewer,
					encryptionService.missingKeyHint(), false, null);
		}
		Map<String, Object> beforeRow = parseJsonMap(task.getBeforeRowJson());
		Map<String, Object> writebackPayload = parseJsonMap(task.getWritebackPayloadJson());
		boolean hardDeleteAction = "HARD_DELETE".equals(task.getActionSuggested());
		if (beforeRow.isEmpty() || (!hardDeleteAction && writebackPayload.isEmpty())) {
			return completeApprovedTask(task, CleaningReviewStatus.FAILED.name(), reviewer, reason, false, null);
		}
		PkRef pkRef = resolvePk(task.getPkJson());
		if (pkRef == null) {
			return completeApprovedTask(task, CleaningReviewStatus.FAILED.name(), reviewer, reason, false, null);
		}
		Datasource datasource = datasourceService.getDatasourceById(task.getDatasourceId());
		if (datasource == null) {
			return completeApprovedTask(task, CleaningReviewStatus.FAILED.name(), reviewer, reason, false, null);
		}
		DBConnectionPool pool = connectionPoolFactory.getPoolByDbType(datasource.getType());
		try (Connection connection = pool.getConnection(datasourceService.getDbConfig(datasource))) {
			Map<String, CleaningWritebackValidator.ColumnMeta> columnMeta = CleaningWritebackValidator
				.loadColumnMeta(connection, task.getTableName());
			String validationError = CleaningWritebackValidator.validateValues(columnMeta, writebackPayload);
			if (validationError != null) {
				return completeApprovedTask(task, CleaningReviewStatus.FAILED.name(), reviewer, validationError, false,
						null);
			}
			if (!matchesCurrentRow(connection, task.getTableName(), pkRef, beforeRow)) {
				return completeApprovedTask(task, CleaningReviewStatus.CONFLICT.name(), reviewer, reason, false, null);
			}
			backupBeforeRow(task, beforeRow);
			if (hardDeleteAction) {
				executeDelete(connection, task.getTableName(), pkRef);
			}
			else {
				executeUpdate(connection, task.getTableName(), writebackPayload, pkRef);
			}
			return completeApprovedTask(task, CleaningReviewStatus.WRITTEN.name(), reviewer, reason, true,
					task.getActionSuggested());
		}
		catch (Exception e) {
			log.warn("Failed to writeback review task {}", task.getId(), e);
			return completeApprovedTask(task, CleaningReviewStatus.FAILED.name(), reviewer, reason, false, null);
		}
	}

	private void updateStatus(Long id, String status, String reviewer, String reason) {
		reviewTaskMapper.updateStatusIfMatch(id, CleaningReviewStatus.APPROVED.name(), status, reviewer, reason,
				LocalDateTime.now());
	}

	private CleaningReviewTask completeApprovedTask(CleaningReviewTask task, String toStatus, String reviewer,
			String reason, boolean appendAuditRecord, String actionTaken) {
		updateStatus(task.getId(), toStatus, reviewer, reason);
		if (appendAuditRecord) {
			appendReviewRecord(task, actionTaken);
		}
		CleaningReviewTask latest = reviewTaskMapper.selectById(task.getId());
		appendFeedbackRecord(latest != null ? latest : task, reviewer, reason);
		return latest != null ? latest : task;
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
		String sql = "SELECT " + columns + " FROM " + tableName + " WHERE " + buildPkWhereClause(pkRef) + " LIMIT 1";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bindPkValues(statement, 1, pkRef);
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

	private void executeDelete(Connection connection, String tableName, PkRef pkRef) throws Exception {
		String sql = "DELETE FROM " + tableName + " WHERE " + buildPkWhereClause(pkRef);
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			bindPkValues(statement, 1, pkRef);
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

	private void appendFeedbackRecord(CleaningReviewTask task, String reviewer, String reason) {
		if (task == null) {
			return;
		}
		reviewFeedbackRecordMapper.insert(CleaningReviewFeedbackRecord.builder()
			.reviewTaskId(task.getId())
			.jobRunId(task.getJobRunId())
			.agentId(task.getAgentId())
			.datasourceId(task.getDatasourceId())
			.tableName(task.getTableName())
			.pkHash(task.getPkHash() != null ? task.getPkHash() : hashPk(task.getPkJson()))
			.columnName(task.getColumnName())
			.verdict(task.getVerdict())
			.categoriesJson(task.getCategoriesJson())
			.actionSuggested(task.getActionSuggested())
			.finalStatus(task.getStatus())
			.reviewer(reviewer)
			.reviewReason(reason)
			.sanitizedPreview(task.getSanitizedPreview())
			.policySnapshotJson(resolvePolicySnapshot(task.getJobRunId()))
			.createdTime(LocalDateTime.now())
			.build());
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

	private String resolveEscalateReason(String reason, int overdueHours) {
		if (reason != null && !reason.isBlank()) {
			return reason;
		}
		return "AUTO_ESCALATED: pending over " + overdueHours + "h";
	}

	private int resolveEscalateHours(Integer hours) {
		if (hours == null || hours <= 0) {
			return DEFAULT_ESCALATE_HOURS;
		}
		return Math.min(hours, (int) Duration.ofDays(7).toHours());
	}

	private int resolveEscalateLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_ESCALATE_LIMIT;
		}
		return Math.min(limit, MAX_ESCALATE_LIMIT);
	}

	private int resolveFeedbackLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_FEEDBACK_LIMIT;
		}
		return Math.min(limit, MAX_FEEDBACK_LIMIT);
	}

	private String hashPk(String pkJson) {
		try {
			String canonicalPkJson = canonicalizePkJson(pkJson);
			if (canonicalPkJson == null) {
				canonicalPkJson = pkJson != null ? pkJson : "";
			}
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(canonicalPkJson.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hash);
		}
		catch (Exception e) {
			return null;
		}
	}

	private String canonicalizePkJson(String pkJson) {
		Map<String, Object> pkMap = parseJsonMap(pkJson);
		if (pkMap.isEmpty()) {
			return pkJson != null ? pkJson : "";
		}
		return toJsonSafe(new TreeMap<>(pkMap));
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

	private record PkRef(List<String> columns, List<Object> values) {
	}

}
