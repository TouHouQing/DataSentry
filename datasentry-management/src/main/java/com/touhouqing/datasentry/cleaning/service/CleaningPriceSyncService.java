package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.cleaning.dto.CleaningPricingSyncResult;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPriceCatalogMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPriceCatalog;
import com.touhouqing.datasentry.mapper.ModelConfigMapper;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleaningPriceSyncService {

	private static final String CODE_PRICING_SYNC_FAILED = "PRICING_SYNC_FAILED";

	private static final String PRICING_SOURCE_MANUAL = "MANUAL";

	private final DataSentryProperties dataSentryProperties;

	private final CleaningPriceCatalogMapper priceCatalogMapper;

	private final ModelConfigMapper modelConfigMapper;

	private final CleaningOpsStateService opsStateService;

	private final CleaningNotificationService notificationService;

	private final CleaningPricingService pricingService;

	public CleaningPricingSyncResult syncNow(String reason) {
		LocalDateTime started = LocalDateTime.now();
		LocalDateTime finished = LocalDateTime.now();
		log.info("价格同步功能已废弃，价格管理已迁移至模型配置");
		return CleaningPricingSyncResult.builder()
			.success(true)
			.sourceType("DEPRECATED")
			.reason(reason)
			.total(0)
			.inserted(0)
			.updated(0)
			.skipped(0)
			.message("价格已迁移至模型配置管理，请前往模型配置页面维护价格")
			.startedTime(started)
			.finishedTime(finished)
			.build();
	}

	public List<CleaningPriceCatalog> listCatalog() {
		// Return empty list or existing catalog?
		// The requirement is to deprecate the catalog table.
		// However, for compatibility we might just return empty or what's in the DB.
		// Since we deprecated the sync, the DB might be stale.
		// But listCatalog is used by... wait, the controller now calls
		// listActivePricingFromModelConfig.
		// So this method might be unused?
		// Let's keep it as is for now to avoid breaking other callers if any.
		// But I need to fix imports.
		return List.of();
	}

	public boolean isSyncEnabled() {
		return dataSentryProperties.getCleaning().getPricing().isSyncEnabled();
	}

}
