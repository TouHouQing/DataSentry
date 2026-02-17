package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.mapper.CleaningBackupRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningDlqMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewFeedbackRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackConflictRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackVerifyRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningShadowCompareRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.cleaning.service.CleaningLifecycleService;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CleaningLifecycleServiceTest {

	@Mock
	private CleaningBackupRecordMapper backupRecordMapper;

	@Mock
	private CleaningRecordMapper recordMapper;

	@Mock
	private CleaningReviewTaskMapper reviewTaskMapper;

	@Mock
	private CleaningReviewFeedbackRecordMapper reviewFeedbackRecordMapper;

	@Mock
	private CleaningShadowCompareRecordMapper shadowCompareRecordMapper;

	@Mock
	private CleaningRollbackVerifyRecordMapper rollbackVerifyRecordMapper;

	@Mock
	private CleaningRollbackConflictRecordMapper rollbackConflictRecordMapper;

	@Mock
	private CleaningRollbackRunMapper rollbackRunMapper;

	@Mock
	private CleaningDlqMapper dlqMapper;

	private DataSentryProperties properties;

	private CleaningLifecycleService lifecycleService;

	@BeforeEach
	public void setUp() {
		properties = new DataSentryProperties();
		properties.getCleaning().setEnabled(true);
		properties.getCleaning().getLifecycle().setEnabled(true);
		properties.getCleaning().getLifecycle().setBatchLimit(2);
		lifecycleService = new CleaningLifecycleService(properties, backupRecordMapper, recordMapper, reviewTaskMapper,
				reviewFeedbackRecordMapper, shadowCompareRecordMapper, rollbackVerifyRecordMapper,
				rollbackConflictRecordMapper, rollbackRunMapper, dlqMapper);
		when(recordMapper.deleteExpired(any(), anyInt())).thenReturn(0);
		when(reviewTaskMapper.deleteExpired(any(), anyInt())).thenReturn(0);
		when(reviewFeedbackRecordMapper.deleteExpired(any(), anyInt())).thenReturn(0);
		when(shadowCompareRecordMapper.deleteExpired(any(), anyInt())).thenReturn(0);
		when(rollbackVerifyRecordMapper.deleteExpired(any(), anyInt())).thenReturn(0);
		when(rollbackConflictRecordMapper.deleteExpired(any(), anyInt())).thenReturn(0);
		when(rollbackRunMapper.deleteExpiredFinished(any(), anyInt())).thenReturn(0);
		when(dlqMapper.deleteExpiredFinished(any(), anyInt())).thenReturn(0);
	}

	@Test
	public void shouldSkipWhenLifecycleNotEnabled() {
		properties.getCleaning().getLifecycle().setEnabled(false);

		CleaningLifecycleService.CleaningLifecyclePurgeResult result = lifecycleService.purgeExpiredData();

		assertFalse(result.enabled());
		verify(backupRecordMapper, never()).deleteExpired(any(), anyInt());
	}

	@Test
	public void shouldWriteAuditWhenAnyDataPurged() {
		when(backupRecordMapper.deleteExpired(any(), anyInt())).thenReturn(2, 1);
		when(recordMapper.insert(any(CleaningRecord.class))).thenReturn(1);

		CleaningLifecycleService.CleaningLifecyclePurgeResult result = lifecycleService.purgeExpiredData();

		assertEquals(3L, result.backupDeleted());
		assertEquals(3L, result.totalDeleted());
		ArgumentCaptor<CleaningRecord> captor = ArgumentCaptor.forClass(CleaningRecord.class);
		verify(recordMapper).insert(captor.capture());
		assertEquals(0L, captor.getValue().getAgentId());
		assertEquals("TTL_PURGE", captor.getValue().getActionTaken());
	}

}
