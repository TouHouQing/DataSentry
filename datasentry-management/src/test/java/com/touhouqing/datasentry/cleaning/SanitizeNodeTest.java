package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.enums.CleaningVerdict;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.pipeline.SanitizeNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SanitizeNodeTest {

	private final SanitizeNode sanitizeNode = new SanitizeNode();

	@Test
	public void keepsBlockVerdictWhenSanitizedTextUnchanged() {
		String text = "safe_text_without_spans";
		CleaningContext context = CleaningContext.builder()
			.originalText(text)
			.verdict(CleaningVerdict.BLOCK)
			.findings(List.of(Finding.builder().start(null).end(null).severity(0.9).build()))
			.build();
		context.getMetadata().put("sanitizeRequested", true);

		sanitizeNode.process(context);

		assertEquals(text, context.getSanitizedText());
		assertEquals(CleaningVerdict.BLOCK, context.getVerdict());
	}

	@Test
	public void marksRedactedWhenSanitizedTextChanged() {
		String text = "call 13800138000";
		int start = text.indexOf("13800138000");
		CleaningContext context = CleaningContext.builder()
			.originalText(text)
			.verdict(CleaningVerdict.BLOCK)
			.findings(List.of(Finding.builder().start(start).end(start + 11).severity(0.9).build()))
			.build();
		context.getMetadata().put("sanitizeRequested", true);

		sanitizeNode.process(context);

		assertEquals("call [REDACTED]", context.getSanitizedText());
		assertEquals(CleaningVerdict.REDACTED, context.getVerdict());
	}

}
