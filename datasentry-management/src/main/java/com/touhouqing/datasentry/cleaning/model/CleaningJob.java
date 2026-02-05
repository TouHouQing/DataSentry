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
@TableName("datasentry_cleaning_job")
public class CleaningJob {

	@TableId(type = IdType.AUTO)
	private Long id;

	private Long agentId;

	private Long datasourceId;

	private String tableName;

	private String pkColumnsJson;

	private String targetColumnsJson;

	private String whereSql;

	private Long policyId;

	private String mode;

	private String writebackMode;

	private String reviewPolicy;

	private String backupPolicyJson;

	private String writebackMappingJson;

	private Integer batchSize;

	private Integer concurrency;

	private Integer rateLimit;

	private Integer enabled;

	private LocalDateTime createdTime;

	private LocalDateTime updatedTime;

}
