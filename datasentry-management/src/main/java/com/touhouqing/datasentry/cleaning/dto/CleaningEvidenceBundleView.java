package com.touhouqing.datasentry.cleaning.dto;

import com.touhouqing.datasentry.cleaning.model.CleaningJobRun;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyVersion;
import com.touhouqing.datasentry.cleaning.model.CleaningRecord;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.model.CleaningShadowCompareRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningEvidenceBundleView {

	private Long jobRunId;

	private LocalDateTime exportedTime;

	private CleaningJobRun jobRun;

	private CleaningPolicyVersion policyVersion;

	private Long totalAuditRecords;

	private Long totalReviewTasks;

	private Long totalShadowCompareRecords;

	private Long totalRollbackRuns;

	private Boolean auditRecordsTruncated;

	private Boolean reviewTasksTruncated;

	private Boolean shadowCompareRecordsTruncated;

	private Boolean rollbackRunsTruncated;

	private List<CleaningRecord> auditRecords;

	private List<CleaningReviewTask> reviewTasks;

	private List<CleaningShadowCompareRecord> shadowCompareRecords;

	private List<CleaningEvidenceRollbackView> rollbackRuns;

}
