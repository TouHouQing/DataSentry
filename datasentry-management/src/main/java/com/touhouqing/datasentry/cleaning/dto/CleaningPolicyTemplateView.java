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
public class CleaningPolicyTemplateView {

	private Long id;

	private String name;

	private String description;

	private String category;

	private Integer enabled;

	private String defaultAction;

	private String configJson;

	private List<CleaningPolicyRuleItem> rules;

	private LocalDateTime createdTime;

	private LocalDateTime updatedTime;

}
