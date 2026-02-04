package com.touhouqing.datasentry.cleaning.model;

import com.touhouqing.datasentry.cleaning.enums.CleaningVerdict;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningContext {

	private String originalText;

	private Long agentId;

	private String normalizedText;

	@Builder.Default
	private List<Finding> findings = new ArrayList<>();

	private CleaningVerdict verdict;

	private String sanitizedText;

	private String traceId;

	private CleaningPolicySnapshot policySnapshot;

	@Builder.Default
	private Map<String, Object> metadata = new HashMap<>();

	@Builder.Default
	private Map<String, Object> metrics = new HashMap<>();

}
