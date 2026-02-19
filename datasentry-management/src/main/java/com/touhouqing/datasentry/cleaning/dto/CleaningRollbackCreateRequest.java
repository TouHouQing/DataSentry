package com.touhouqing.datasentry.cleaning.dto;

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
public class CleaningRollbackCreateRequest {

	private List<Long> backupRecordIds;

	private List<Long> recordIds;

	private LocalDateTime startTime;

	private LocalDateTime endTime;

}
