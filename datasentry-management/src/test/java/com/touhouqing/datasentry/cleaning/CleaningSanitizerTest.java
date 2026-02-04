package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.util.CleaningSanitizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CleaningSanitizerTest {

	@Test
	public void replacesFindingsWithMask() {
		String text = "call 13800138000 and mail test@example.com";
		int phoneStart = text.indexOf("13800138000");
		int emailStart = text.indexOf("test@example.com");
		List<Finding> findings = List.of(
			Finding.builder().start(emailStart).end(emailStart + "test@example.com".length()).build(),
			Finding.builder().start(phoneStart).end(phoneStart + 11).build());

		String sanitized = CleaningSanitizer.sanitize(text, findings);

		assertEquals("call [REDACTED] and mail [REDACTED]", sanitized);
	}

	@Test
	public void ignoresInvalidSpans() {
		String text = "call 13800138000";
		List<Finding> findings = List.of(Finding.builder().start(5).end(3).build());

		String sanitized = CleaningSanitizer.sanitize(text, findings);

		assertEquals(text, sanitized);
	}

}
