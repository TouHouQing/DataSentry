package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.enums.CleaningVerdict;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicySnapshot;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.pipeline.DecideNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecideNodeTest {

	@Test
	public void allowWhenNoFindings() {
		DecideNode node = new DecideNode();
		CleaningContext context = CleaningContext.builder().findings(List.of()).build();

		node.process(context);

		assertEquals(CleaningVerdict.ALLOW, context.getVerdict());
	}

	@Test
	public void reviewWhenL3AllParseFailedEvenIfNoFindings() {
		DecideNode node = new DecideNode();
		CleaningContext context = CleaningContext.builder().findings(List.of()).build();
		context.getMetadata().put("l3AllParseFailed", true);

		node.process(context);

		assertEquals(CleaningVerdict.REVIEW, context.getVerdict());
	}

	@Test
	public void blockWhenSeverityExceedsThreshold() {
		DecideNode node = new DecideNode();
		CleaningPolicyConfig config = CleaningPolicyConfig.builder().blockThreshold(0.7).reviewThreshold(0.4).build();
		CleaningPolicySnapshot snapshot = CleaningPolicySnapshot.builder().config(config).build();
		CleaningContext context = CleaningContext.builder()
			.policySnapshot(snapshot)
			.findings(List.of(Finding.builder().severity(0.8).build()))
			.build();

		node.process(context);

		assertEquals(CleaningVerdict.BLOCK, context.getVerdict());
	}

	@Test
	public void reviewWhenSeverityInReviewRange() {
		DecideNode node = new DecideNode();
		CleaningPolicyConfig config = CleaningPolicyConfig.builder().blockThreshold(0.7).reviewThreshold(0.4).build();
		CleaningPolicySnapshot snapshot = CleaningPolicySnapshot.builder().config(config).build();
		CleaningContext context = CleaningContext.builder()
			.policySnapshot(snapshot)
			.findings(List.of(Finding.builder().severity(0.5).build()))
			.build();

		node.process(context);

		assertEquals(CleaningVerdict.REVIEW, context.getVerdict());
	}

}
