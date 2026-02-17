package com.touhouqing.datasentry.cleaning.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("datasentry_cleaning_review_feedback_record")
public class CleaningReviewFeedbackRecord {

	@TableId(type = IdType.AUTO)
	private Long id;

	private Long reviewTaskId;

	private Long jobRunId;

	private Long agentId;

	private Long datasourceId;

	private String tableName;

	private String pkHash;

	private String columnName;

	private String verdict;

	private String categoriesJson;

	private String actionSuggested;

	private String finalStatus;

	private String reviewer;

	private String reviewReason;

	private String sanitizedPreview;

	private String policySnapshotJson;

	private LocalDateTime createdTime;

}
