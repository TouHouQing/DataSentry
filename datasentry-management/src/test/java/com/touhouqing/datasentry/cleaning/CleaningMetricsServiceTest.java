package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.dto.CleaningAlertView;
import com.touhouqing.datasentry.cleaning.mapper.CleaningCostLedgerMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningDlqMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningJobRunMapper;
import com.touhouqing.datasentry.cleaning.mapper.CleaningReviewTaskMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.service.CleaningMetricsService;
import com.touhouqing.datasentry.cleaning.service.CleaningOpsStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CleaningMetricsServiceTest {

	@Mock
	private CleaningJobRunMapper jobRunMapper;

	@Mock
	private CleaningDlqMapper dlqMapper;

	@Mock
	private CleaningCostLedgerMapper costLedgerMapper;

	@Mock
	private CleaningReviewTaskMapper reviewTaskMapper;

	private CleaningOpsStateService opsStateService;

	private CleaningMetricsService metricsService;

	@BeforeEach
	public void setUp() {
		opsStateService = new CleaningOpsStateService();
		metricsService = new CleaningMetricsService(jobRunMapper, dlqMapper, costLedgerMapper, reviewTaskMapper,
				opsStateService);
		when(jobRunMapper.selectCount(any())).thenReturn(0L);
		when(dlqMapper.selectCount(any())).thenReturn(0L);
		when(reviewTaskMapper.selectList(any())).thenReturn(List.of());
	}

	@Test
	public void shouldEmitOnnxDegradedAlertWhenProviderDegraded() {
		opsStateService.setL2ProviderStatus("ONNX/DEGRADED");

		List<CleaningAlertView> alerts = metricsService.alerts();

		assertTrue(alerts.stream().anyMatch(item -> "L2_ONNX_DEGRADED".equals(item.getCode())));
	}

	@Test
	public void shouldEmitCloudDegradedAlertWhenProviderDegraded() {
		opsStateService.setL2ProviderStatus("CLOUD_API/DEGRADED");

		List<CleaningAlertView> alerts = metricsService.alerts();

		assertTrue(alerts.stream().anyMatch(item -> "L2_CLOUD_API_DEGRADED".equals(item.getCode())));
	}

	@Test
	public void shouldEmitReviewOverdueAlertWhenPendingTaskExceededSla() {
		CleaningReviewTask overdueTask = CleaningReviewTask.builder()
			.id(1L)
			.status("PENDING")
			.createdTime(LocalDateTime.now().minusDays(2))
			.build();
		when(reviewTaskMapper.selectList(any())).thenReturn(List.of(overdueTask), List.of());

		List<CleaningAlertView> alerts = metricsService.alerts();

		assertTrue(alerts.stream().anyMatch(item -> "REVIEW_TASK_OVERDUE".equals(item.getCode())));
	}

}
