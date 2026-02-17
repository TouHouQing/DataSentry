package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateResult;
import com.touhouqing.datasentry.cleaning.mapper.CleaningBackupRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewFeedbackRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.service.CleaningBackupEncryptionService;
import com.touhouqing.datasentry.cleaning.service.CleaningReviewService;
import com.touhouqing.datasentry.connector.pool.DBConnectionPoolFactory;
import com.touhouqing.datasentry.service.datasource.DatasourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CleaningReviewServiceEscalationTest {

	@Mock
	private CleaningReviewTaskMapper reviewTaskMapper;

	@Mock
	private CleaningBackupRecordMapper backupRecordMapper;

	@Mock
	private CleaningJobRunMapper jobRunMapper;

	@Mock
	private CleaningRecordMapper recordMapper;

	@Mock
	private CleaningReviewFeedbackRecordMapper reviewFeedbackRecordMapper;

	@Mock
	private CleaningBackupEncryptionService encryptionService;

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private DBConnectionPoolFactory connectionPoolFactory;

	private CleaningReviewService reviewService;

	@BeforeEach
	public void setUp() {
		reviewService = new CleaningReviewService(reviewTaskMapper, backupRecordMapper, reviewFeedbackRecordMapper,
				jobRunMapper, recordMapper, encryptionService, datasourceService, connectionPoolFactory);
	}

	@Test
	public void shouldEscalateOverduePendingTasks() {
		List<CleaningReviewTask> overdueTasks = List.of(
				CleaningReviewTask.builder()
					.id(1L)
					.status("PENDING")
					.createdTime(LocalDateTime.now().minusHours(30))
					.build(),
				CleaningReviewTask.builder()
					.id(2L)
					.status("PENDING")
					.createdTime(LocalDateTime.now().minusHours(40))
					.build());
		when(reviewTaskMapper.selectList(any())).thenReturn(overdueTasks);
		when(reviewTaskMapper.markEscalatedIfPending(any(), any(), any(), any())).thenReturn(1, 0);

		CleaningReviewEscalateResult result = reviewService
			.escalateOverduePending(CleaningReviewEscalateRequest.builder()
				.overdueHours(24)
				.limit(100)
				.reviewer("sla-bot")
				.reason("AUTO_ESCALATED")
				.build());

		assertEquals(2, result.getTotalCandidates());
		assertEquals(1, result.getEscalated());
		assertEquals(1, result.getSkipped());
		assertEquals(24, result.getOverdueHours());
	}

}
