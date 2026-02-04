package com.touhouqing.datasentry.cleaning.model;

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
public class CleaningRecord {

	private Long id;

	private Long agentId;

	private String traceId;

	private String policySnapshotJson;

	private String verdict;

	private String categoriesJson;

	private String sanitizedPreview;

	private String metricsJson;

	private Long executionTimeMs;

	private String detectorSource;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

}
