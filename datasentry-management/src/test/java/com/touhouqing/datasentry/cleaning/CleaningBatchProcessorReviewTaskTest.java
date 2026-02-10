package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.enums.CleaningReviewPolicy;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningJob;
import com.touhouqing.datasentry.cleaning.model.CleaningReviewTask;
import com.touhouqing.datasentry.cleaning.service.CleaningBatchProcessor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CleaningBatchProcessorReviewTaskTest {

	@Test
	public void shouldCreateReviewOnlyTaskWhenVerdictIsReview() throws Exception {
		CleaningBatchProcessor processor = newProcessor();

		CleaningJob job = CleaningJob.builder().agentId(1L).datasourceId(1L).tableName("users").build();

		CleaningContext context = CleaningContext.builder().sanitizedText("masked").build();
		context.setVerdict(com.touhouqing.datasentry.cleaning.enums.CleaningVerdict.REVIEW);

		Method method = CleaningBatchProcessor.class.getDeclaredMethod("buildReviewTask", Long.class, CleaningJob.class,
				String.class, String.class, CleaningContext.class, boolean.class, boolean.class, boolean.class,
				boolean.class, Map.class, Map.class, Map.class);
		method.setAccessible(true);

		CleaningReviewTask task = (CleaningReviewTask) method.invoke(processor, 21L, job, "{\"id\":10}", "email",
				context, false, false, false, true, Map.of("email", "email"), Map.of(), Map.of("email", "a@b.com"));

		assertNotNull(task);
		assertEquals("REVIEW_ONLY", task.getActionSuggested());
		assertEquals("PENDING", task.getStatus());
	}

	@Test
	public void shouldRequireReviewForReviewVerdictOnRiskPolicy() throws Exception {
		CleaningBatchProcessor processor = newProcessor();

		Method method = CleaningBatchProcessor.class.getDeclaredMethod("isReviewRequired", CleaningReviewPolicy.class,
				boolean.class, String.class);
		method.setAccessible(true);

		Boolean required = (Boolean) method.invoke(processor, CleaningReviewPolicy.ON_RISK, false, "REVIEW");
		assertEquals(true, required);
	}

	@Test
	public void shouldCreateReviewOnlyTaskWhenVerdictIsRedactedWithoutWritebackCandidate() throws Exception {
		CleaningBatchProcessor processor = newProcessor();

		CleaningJob job = CleaningJob.builder().agentId(1L).datasourceId(1L).tableName("users").build();

		CleaningContext context = CleaningContext.builder().sanitizedText("same-text").build();
		context.setVerdict(com.touhouqing.datasentry.cleaning.enums.CleaningVerdict.REDACTED);

		Method method = CleaningBatchProcessor.class.getDeclaredMethod("buildReviewTask", Long.class, CleaningJob.class,
				String.class, String.class, CleaningContext.class, boolean.class, boolean.class, boolean.class,
				boolean.class, Map.class, Map.class, Map.class);
		method.setAccessible(true);

		CleaningReviewTask task = (CleaningReviewTask) method.invoke(processor, 22L, job, "{\"id\":11}", "email",
				context, false, false, false, true, Map.of("email", "email"), Map.of(), Map.of("email", "a@b.com"));

		assertNotNull(task);
		assertEquals("REVIEW_ONLY", task.getActionSuggested());
		assertEquals("PENDING", task.getStatus());
	}

	private CleaningBatchProcessor newProcessor() {
		return new CleaningBatchProcessor(null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null);
	}

}
