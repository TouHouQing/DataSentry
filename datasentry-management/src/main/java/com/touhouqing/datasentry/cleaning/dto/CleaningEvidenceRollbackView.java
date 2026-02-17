package com.touhouqing.datasentry.cleaning.dto;

import com.touhouqing.datasentry.cleaning.model.CleaningRollbackConflictRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackRun;
import com.touhouqing.datasentry.cleaning.model.CleaningRollbackVerifyRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningEvidenceRollbackView {

	private CleaningRollbackRun rollbackRun;

	private Long totalVerifyRecords;

	private Long totalConflictRecords;

	private Boolean verifyRecordsTruncated;

	private Boolean conflictRecordsTruncated;

	private List<CleaningRollbackVerifyRecord> verifyRecords;

	private List<CleaningRollbackConflictRecord> conflictRecords;

}
