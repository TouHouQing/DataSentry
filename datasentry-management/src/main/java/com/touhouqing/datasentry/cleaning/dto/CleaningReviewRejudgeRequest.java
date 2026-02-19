package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningReviewRejudgeRequest {

	private Integer version;

	private String reviewer;

	private String reason;

	private String verdict;

	private String actionSuggested;

	private String sanitizedPreview;

	private String writebackPayloadJson;

}
