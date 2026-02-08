package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.dto.CleaningPricingSyncResult;
import com.touhouqing.datasentry.cleaning.mapper.CleaningPriceCatalogMapper;
import com.touhouqing.datasentry.cleaning.service.CleaningNotificationService;
import com.touhouqing.datasentry.cleaning.service.CleaningOpsStateService;
import com.touhouqing.datasentry.cleaning.service.CleaningPriceSyncService;
import com.touhouqing.datasentry.cleaning.service.CleaningPricingService;
import com.touhouqing.datasentry.mapper.ModelConfigMapper;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class CleaningPriceSyncServiceTest {

	@Mock
	private CleaningPriceCatalogMapper priceCatalogMapper;

	@Mock
	private ModelConfigMapper modelConfigMapper;

	@Mock
	private CleaningNotificationService notificationService;

	@Mock
	private CleaningOpsStateService opsStateService;

	@Mock
	private CleaningPricingService pricingService;

	@Test
	public void shouldReturnDeprecatedStatus() {
		DataSentryProperties properties = new DataSentryProperties();
		CleaningPriceSyncService syncService = new CleaningPriceSyncService(properties, priceCatalogMapper,
				modelConfigMapper, opsStateService, notificationService, pricingService);

		CleaningPricingSyncResult result = syncService.syncNow("test");

		assertTrue(result.isSuccess());
		assertEquals("DEPRECATED", result.getSourceType());
		assertEquals(0, result.getInserted());
		assertEquals(0, result.getUpdated());
	}
}
