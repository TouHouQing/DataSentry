package com.touhouqing.datasentry.cleaning.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningAllowlist {

	private Long id;

	private String name;

	private String type;

	private String value;

	private String category;

	private String scopeType;

	private Long scopeId;

	private Integer enabled;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime expireTime;

	private String createdBy;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

}
