package com.touhouqing.datasentry.cleaning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningPolicyExperimentView {

	private Long ticketId;

	private Long policyId;

	private Long versionId;

	private String action;

	private String publishMode;

	private BigDecimal grayRatio;

	private String experimentName;

	private String note;

	private String operator;

	private LocalDateTime createdTime;

}
