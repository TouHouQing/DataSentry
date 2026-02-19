package com.touhouqing.datasentry.cleaning.dto;

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
public class CleaningPolicyTemplateRequest {

	private String name;

	private String description;

	private String category;

	private Integer enabled;

	private String defaultAction;

	private Map<String, Object> config;

	private List<CleaningPolicyRuleItem> rules;

}
