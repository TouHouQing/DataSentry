package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningReviewEscalateResult {

	private Integer totalCandidates;

	private Integer escalated;

	private Integer skipped;

	private Integer overdueHours;

}
