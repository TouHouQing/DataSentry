package com.touhouqing.datasentry.cleaning.batch;

import com.touhouqing.datasentry.cleaning.service.CleaningPriceSyncService;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CleaningPriceSyncScheduler {

	private final CleaningPriceSyncService priceSyncService;

	private final DataSentryProperties dataSentryProperties;

	@Scheduled(fixedDelayString = "${spring.ai.alibaba.datasentry.cleaning.pricing.sync-interval-ms:1800000}")
	public void sync() {
		// 价格同步功能已废弃，价格管理已迁移至模型配置
		return;
	}

}
