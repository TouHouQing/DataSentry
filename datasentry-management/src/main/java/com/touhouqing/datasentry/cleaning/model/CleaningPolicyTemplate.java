package com.touhouqing.datasentry.cleaning.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("datasentry_cleaning_policy_template")
public class CleaningPolicyTemplate {

	@TableId(type = IdType.AUTO)
	private Long id;

	private String name;

	private String description;

	private String category;

	private Integer enabled;

	private String defaultAction;

	private String configJson;

	private String rulesJson;

	private LocalDateTime createdTime;

	private LocalDateTime updatedTime;

}
