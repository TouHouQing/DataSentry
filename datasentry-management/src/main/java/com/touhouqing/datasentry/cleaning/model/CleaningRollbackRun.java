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
@TableName("datasentry_cleaning_rollback_run")
public class CleaningRollbackRun {

	@TableId(type = IdType.AUTO)
	private Long id;

	private Long jobRunId;

	private String status;

	private Long checkpointId;

	private Long totalTarget;

	private Long totalSuccess;

	private Long totalFailed;

	private String verifyStatus;

	private String conflictLevelSummary;

	private String selectorJson;

	private LocalDateTime startedTime;

	private LocalDateTime endedTime;

	private LocalDateTime createdTime;

	private LocalDateTime updatedTime;

}
