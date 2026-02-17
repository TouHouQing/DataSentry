package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.batch.CleaningLifecycleScheduler;
import com.touhouqing.datasentry.cleaning.service.CleaningLifecycleService;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CleaningLifecycleSchedulerTest {

	@Mock
	private CleaningLifecycleService lifecycleService;

	private DataSentryProperties properties;

	private CleaningLifecycleScheduler scheduler;

	@BeforeEach
	public void setUp() {
		properties = new DataSentryProperties();
		properties.getCleaning().setEnabled(true);
		properties.getCleaning().getLifecycle().setEnabled(true);
		scheduler = new CleaningLifecycleScheduler(lifecycleService, properties);
	}

	@Test
	public void shouldSkipWhenLifecycleDisabled() {
		properties.getCleaning().getLifecycle().setEnabled(false);
		scheduler.poll();
		verify(lifecycleService, never()).purgeExpiredData();
	}

	@Test
	public void shouldExecutePurgeWhenLifecycleEnabled() {
		when(lifecycleService.purgeExpiredData()).thenReturn(new CleaningLifecycleService.CleaningLifecyclePurgeResult(
				true, 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

		scheduler.poll();

		verify(lifecycleService).purgeExpiredData();
	}

}
