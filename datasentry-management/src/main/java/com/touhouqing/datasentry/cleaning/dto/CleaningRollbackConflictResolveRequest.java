package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningRollbackConflictResolveRequest {

	private List<Long> conflictIds;

	private Long rollbackRunId;

	private String level;

	private Integer limit;

}
