package com.touhouqing.datasentry.cleaning.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("datasentry_cleaning_cost_ledger")
public class CleaningCostLedger {

	@TableId(type = IdType.AUTO)
	private Long id;

	@TableField(exist = false)
	private String agentName;

	private Long jobId;

	private Long jobRunId;

	private Long agentId;

	private String traceId;

	private String channel;

	private String detectorLevel;

	private String provider;

	private String model;

	private Long inputTokensEst;

	private Long outputTokensEst;

	private BigDecimal unitPriceIn;

	private BigDecimal unitPriceOut;

	private BigDecimal costAmount;

	private String currency;

	private LocalDateTime createdTime;

}
