package com.touhouqing.datasentry.cleaning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningJobCreateRequest {

	@NotNull
	private Long agentId;

	@NotNull
	private Long datasourceId;

	@NotBlank
	private String tableName;

	@NotEmpty
	private List<String> pkColumns;

	@NotEmpty
	private List<String> targetColumns;

	private String whereSql;

	@NotNull
	private Long policyId;

	private String mode;

	private String writebackMode;

	private String reviewPolicy;

	private Map<String, Object> writebackMapping;

	private Integer batchSize;

	private Integer enabled;

}
