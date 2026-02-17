package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.batch.CleaningReviewScheduler;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateResult;
import com.touhouqing.datasentry.cleaning.service.CleaningReviewService;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CleaningReviewSchedulerTest {

	@Mock
	private CleaningReviewService reviewService;

	private DataSentryProperties properties;

	private CleaningReviewScheduler scheduler;

	@BeforeEach
	public void setUp() {
		properties = new DataSentryProperties();
		properties.getCleaning().setEnabled(true);
		scheduler = new CleaningReviewScheduler(reviewService, properties);
	}

	@Test
	public void shouldSkipWhenAutoEscalationDisabled() {
		properties.getCleaning().getReview().setAutoEscalationEnabled(false);
		scheduler.poll();
		verify(reviewService, never()).escalateOverduePending(any());
	}

	@Test
	public void shouldEscalateWithConfiguredThreshold() {
		properties.getCleaning().getReview().setAutoEscalationEnabled(true);
		properties.getCleaning().getReview().setOverdueHours(36);
		properties.getCleaning().getReview().setBatchLimit(123);
		properties.getCleaning().getReview().setReviewer("bot-x");
		when(reviewService.escalateOverduePending(any())).thenReturn(CleaningReviewEscalateResult.builder()
			.totalCandidates(0)
			.escalated(0)
			.skipped(0)
			.overdueHours(36)
			.build());

		scheduler.poll();

		ArgumentCaptor<CleaningReviewEscalateRequest> captor = ArgumentCaptor
			.forClass(CleaningReviewEscalateRequest.class);
		verify(reviewService).escalateOverduePending(captor.capture());
		CleaningReviewEscalateRequest request = captor.getValue();
		assertEquals(36, request.getOverdueHours());
		assertEquals(123, request.getLimit());
		assertEquals("bot-x", request.getReviewer());
	}

}
