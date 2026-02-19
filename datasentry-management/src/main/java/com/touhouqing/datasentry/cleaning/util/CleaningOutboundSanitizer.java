package com.touhouqing.datasentry.cleaning.util;

import java.util.regex.Pattern;

public final class CleaningOutboundSanitizer {

	private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");

	private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");

	private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

	private static final Pattern BANK_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{12,19}(?!\\d)");

	private CleaningOutboundSanitizer() {
	}

	public static String sanitize(String text, String mode) {
		if (text == null || text.isBlank()) {
			return text;
		}
		String normalizedMode = mode != null ? mode.trim().toUpperCase() : "MASK_PII";
		if (!"MASK_PII".equals(normalizedMode)) {
			return text;
		}
		String sanitized = PHONE_PATTERN.matcher(text).replaceAll("[PHONE]");
		sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[EMAIL]");
		sanitized = ID_CARD_PATTERN.matcher(sanitized).replaceAll("[ID_CARD]");
		return BANK_CARD_PATTERN.matcher(sanitized).replaceAll("[BANK_CARD]");
	}

}
