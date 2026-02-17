package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningRollbackConflictResolveResult {

	private Integer totalCandidates;

	private Integer resolved;

	private Integer skipped;

}
