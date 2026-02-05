package com.touhouqing.datasentry.cleaning.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("datasentry_cleaning_record")
public class CleaningRecord {

	@TableId(type = IdType.AUTO)
	private Long id;

	private Long agentId;

	private String traceId;

	private Long jobRunId;

	private Long datasourceId;

	private String tableName;

	private String pkJson;

	private String columnName;

	private String actionTaken;

	private String policySnapshotJson;

	private String verdict;

	private String categoriesJson;

	private String sanitizedPreview;

	private String evidenceJson;

	private String metricsJson;

	private Long executionTimeMs;

	private String detectorSource;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

}
