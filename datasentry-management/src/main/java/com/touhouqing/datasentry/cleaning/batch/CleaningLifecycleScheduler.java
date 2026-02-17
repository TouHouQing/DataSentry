package com.touhouqing.datasentry.cleaning.batch;

import com.touhouqing.datasentry.cleaning.service.CleaningLifecycleService;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleaningLifecycleScheduler {

	private final CleaningLifecycleService lifecycleService;

	private final DataSentryProperties dataSentryProperties;

	@Scheduled(fixedDelayString = "${spring.ai.alibaba.datasentry.cleaning.lifecycle.poll-interval-ms:3600000}")
	public void poll() {
		if (!dataSentryProperties.getCleaning().isEnabled()
				|| !dataSentryProperties.getCleaning().getLifecycle().isEnabled()) {
			return;
		}
		CleaningLifecycleService.CleaningLifecyclePurgeResult result = lifecycleService.purgeExpiredData();
		if (result.totalDeleted() > 0) {
			log.info(
					"Cleaning lifecycle purge done: backup={}, audit={}, review={}, reviewFeedback={}, shadow={}, rollbackVerify={}, rollbackConflict={}, rollbackRun={}, dlq={}",
					result.backupDeleted(), result.auditDeleted(), result.reviewDeleted(),
					result.reviewFeedbackDeleted(), result.shadowDeleted(), result.rollbackVerifyDeleted(),
					result.rollbackConflictDeleted(), result.rollbackRunDeleted(), result.dlqDeleted());
		}
	}

}
