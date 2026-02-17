package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.dto.CleaningRollbackConflictResolveRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningRollbackConflictResolveResult;
import com.touhouqing.datasentry.cleaning.mapper.CleaningBackupRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackConflictRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackVerifyRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackConflictRecord;
import com.touhouqing.datasentry.cleaning.service.CleaningBackupEncryptionService;
import com.touhouqing.datasentry.cleaning.service.CleaningRollbackService;
import com.touhouqing.datasentry.connector.pool.DBConnectionPoolFactory;
import com.touhouqing.datasentry.exception.InvalidInputException;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.service.datasource.DatasourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CleaningRollbackServiceConflictResolutionTest {

	@Mock
	private CleaningRollbackRunMapper rollbackRunMapper;

	@Mock
	private CleaningBackupRecordMapper backupRecordMapper;

	@Mock
	private CleaningJobRunMapper jobRunMapper;

	@Mock
	private CleaningJobMapper jobMapper;

	@Mock
	private DatasourceService datasourceService;

	@Mock
	private DBConnectionPoolFactory connectionPoolFactory;

	@Mock
	private CleaningBackupEncryptionService encryptionService;

	@Mock
	private CleaningRollbackVerifyRecordMapper rollbackVerifyRecordMapper;

	@Mock
	private CleaningRollbackConflictRecordMapper rollbackConflictRecordMapper;

	private CleaningRollbackService rollbackService;

	@BeforeEach
	public void setUp() {
		rollbackService = new CleaningRollbackService(rollbackRunMapper, backupRecordMapper, jobRunMapper, jobMapper,
				datasourceService, connectionPoolFactory, encryptionService, rollbackVerifyRecordMapper,
				rollbackConflictRecordMapper, new DataSentryProperties());
	}

	@Test
	public void shouldResolveConflictsByRollbackRun() {
		when(rollbackConflictRecordMapper.selectList(any())).thenReturn(List.of(
				CleaningRollbackConflictRecord.builder().id(1L).resolved(0).build(),
				CleaningRollbackConflictRecord.builder().id(2L).resolved(0).build()));
		when(rollbackConflictRecordMapper.resolveIfUnresolved(any())).thenReturn(1, 0);

		CleaningRollbackConflictResolveResult result = rollbackService
			.resolveConflictRecords(CleaningRollbackConflictResolveRequest.builder().rollbackRunId(10L).level("HIGH").build());

		assertEquals(2, result.getTotalCandidates());
		assertEquals(1, result.getResolved());
		assertEquals(1, result.getSkipped());
	}

	@Test
	public void shouldThrowWhenResolveRequestMissingTarget() {
		assertThrows(InvalidInputException.class,
				() -> rollbackService.resolveConflictRecords(CleaningRollbackConflictResolveRequest.builder().build()));
	}

}
