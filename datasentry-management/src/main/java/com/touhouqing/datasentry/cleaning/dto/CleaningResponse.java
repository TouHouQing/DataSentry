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
public class CleaningResponse {

	private String verdict;

	private List<String> categories;

	private String sanitizedText;

	private String traceId;

	private Long policyId;

	private Long policyVersionId;

	private Boolean shadowCompared;

	private String shadowDiffLevel;

}
