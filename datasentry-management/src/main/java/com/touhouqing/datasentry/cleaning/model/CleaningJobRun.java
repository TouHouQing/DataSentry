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
@TableName("datasentry_cleaning_job_run")
public class CleaningJobRun {

	@TableId(type = IdType.AUTO)
	private Long id;

	private Long jobId;

	private String status;

	private String leaseOwner;

	private LocalDateTime leaseUntil;

	private LocalDateTime heartbeatTime;

	private Integer attempt;

	private String checkpointJson;

	private String policySnapshotJson;

	private Long totalScanned;

	private Long totalFlagged;

	private Long totalWritten;

	private Long totalFailed;

	private LocalDateTime startedTime;

	private LocalDateTime endedTime;

	private LocalDateTime createdTime;

	private LocalDateTime updatedTime;

}
