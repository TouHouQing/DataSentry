package com.touhouqing.datasentry.cleaning.batch;

import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateRequest;
import com.touhouqing.datasentry.cleaning.dto.CleaningReviewEscalateResult;
import com.touhouqing.datasentry.cleaning.service.CleaningReviewService;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleaningReviewScheduler {

	private final CleaningReviewService reviewService;

	private final DataSentryProperties dataSentryProperties;

	@Scheduled(fixedDelayString = "${spring.ai.alibaba.datasentry.cleaning.batch.poll-interval-ms:5000}")
	public void poll() {
		DataSentryProperties.Cleaning cleaning = dataSentryProperties.getCleaning();
		if (!cleaning.isEnabled() || !cleaning.getReview().isAutoEscalationEnabled()) {
			return;
		}
		CleaningReviewEscalateResult result = reviewService
			.escalateOverduePending(CleaningReviewEscalateRequest.builder()
				.overdueHours(cleaning.getReview().getOverdueHours())
				.limit(cleaning.getReview().getBatchLimit())
				.reviewer(cleaning.getReview().getReviewer())
				.reason(null)
				.build());
		if (result.getEscalated() != null && result.getEscalated() > 0) {
			log.info("Auto escalated overdue review tasks: candidates={}, escalated={}, skipped={}, overdueHours={}",
					result.getTotalCandidates(), result.getEscalated(), result.getSkipped(), result.getOverdueHours());
		}
	}

}
