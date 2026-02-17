package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningReviewEscalateRequest {

	private Integer overdueHours;

	private Integer limit;

	private String reviewer;

	private String reason;

}
