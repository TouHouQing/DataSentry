package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.dto.CleaningEvidenceBundleView;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPolicyVersionMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackConflictRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningRollbackVerifyRecordMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningShadowCompareRecordMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyVersion;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackConflictRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackRun;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackVerifyRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningShadowCompareRecord;
import com.touhouqing.datasentry.cleaning.service.CleaningEvidenceService;
import com.touhouqing.datasentry.exception.InvalidInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CleaningEvidenceServiceTest {

	@Mock
	private CleaningJobRunMapper jobRunMapper;

	@Mock
	private CleaningPolicyVersionMapper policyVersionMapper;

	@Mock
	private CleaningRecordMapper recordMapper;

	@Mock
	private CleaningReviewTaskMapper reviewTaskMapper;

	@Mock
	private CleaningShadowCompareRecordMapper shadowCompareRecordMapper;

	@Mock
	private CleaningRollbackRunMapper rollbackRunMapper;

	@Mock
	private CleaningRollbackVerifyRecordMapper rollbackVerifyRecordMapper;

	@Mock
	private CleaningRollbackConflictRecordMapper rollbackConflictRecordMapper;

	private CleaningEvidenceService evidenceService;

	@BeforeEach
	public void setUp() {
		evidenceService = new CleaningEvidenceService(jobRunMapper, policyVersionMapper, recordMapper, reviewTaskMapper,
				shadowCompareRecordMapper, rollbackRunMapper, rollbackVerifyRecordMapper, rollbackConflictRecordMapper);
	}

	@Test
	public void shouldThrowWhenRunMissing() {
		when(jobRunMapper.selectById(100L)).thenReturn(null);

		assertThrows(InvalidInputException.class, () -> evidenceService.exportByRunId(100L));
	}

	@Test
	public void shouldBuildEvidenceBundle() {
		CleaningJobRun run = CleaningJobRun.builder().id(1L).policyVersionId(9L).build();
		CleaningPolicyVersion version = CleaningPolicyVersion.builder().id(9L).build();
		CleaningRollbackRun rollbackRun = CleaningRollbackRun.builder().id(7L).jobRunId(1L).build();
		when(jobRunMapper.selectById(1L)).thenReturn(run);
		when(policyVersionMapper.selectById(9L)).thenReturn(version);
		when(recordMapper.selectCount(any())).thenReturn(1L);
		when(recordMapper.selectList(any())).thenReturn(List.of(CleaningRecord.builder().id(10L).build()));
		when(reviewTaskMapper.selectCount(any())).thenReturn(1L);
		when(reviewTaskMapper.selectList(any())).thenReturn(List.of(CleaningReviewTask.builder().id(11L).build()));
		when(shadowCompareRecordMapper.selectCount(any())).thenReturn(1L);
		when(shadowCompareRecordMapper.selectList(any()))
			.thenReturn(List.of(CleaningShadowCompareRecord.builder().id(12L).build()));
		when(rollbackRunMapper.selectCount(any())).thenReturn(1L);
		when(rollbackRunMapper.selectList(any())).thenReturn(List.of(rollbackRun));
		when(rollbackVerifyRecordMapper.selectCount(any())).thenReturn(1L);
		when(rollbackVerifyRecordMapper.selectList(any()))
			.thenReturn(List.of(CleaningRollbackVerifyRecord.builder().id(13L).build()));
		when(rollbackConflictRecordMapper.selectCount(any())).thenReturn(1L);
		when(rollbackConflictRecordMapper.selectList(any()))
			.thenReturn(List.of(CleaningRollbackConflictRecord.builder().id(14L).build()));

		CleaningEvidenceBundleView bundle = evidenceService.exportByRunId(1L);

		assertEquals(1L, bundle.getTotalAuditRecords());
		assertEquals(1L, bundle.getTotalReviewTasks());
		assertEquals(1L, bundle.getTotalShadowCompareRecords());
		assertEquals(1L, bundle.getTotalRollbackRuns());
		assertEquals(1, bundle.getRollbackRuns().size());
		assertTrue(Boolean.FALSE.equals(bundle.getAuditRecordsTruncated()));
	}

}
