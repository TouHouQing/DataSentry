package com.touhouqing.datasentry.cleaning.util;

import com.touhouqing.datasentry.cleaning.model.Finding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CleaningSanitizer {

	private static final String DEFAULT_MASK = "[REDACTED]";

	private CleaningSanitizer() {
	}

	public static String sanitize(String text, List<Finding> findings) {
		if (text == null || text.isEmpty() || findings == null || findings.isEmpty()) {
			return text;
		}
		List<Finding> valid = new ArrayList<>();
		for (Finding finding : findings) {
			if (finding.getStart() == null || finding.getEnd() == null) {
				continue;
			}
			int start = finding.getStart();
			int end = finding.getEnd();
			if (start < 0 || end <= start || end > text.length()) {
				continue;
			}
			valid.add(finding);
		}
		if (valid.isEmpty()) {
			return text;
		}
		// Sort by start position. If starts are equal, prioritize findings with replacement text over deletions.
		valid.sort(Comparator.comparingInt(Finding::getStart)
			.thenComparingInt(f -> {
				String r = resolveReplacement(f);
				return (r == null || r.isEmpty()) ? 1 : 0;
			}));
		StringBuilder builder = new StringBuilder();
		int cursor = 0;
		for (Finding finding : valid) {
			int start = finding.getStart();
			int end = finding.getEnd();
			if (start < cursor) {
				continue;
			}
			builder.append(text, cursor, start).append(resolveReplacement(finding));
			cursor = end;
		}
		if (cursor < text.length()) {
			builder.append(text.substring(cursor));
		}
		return builder.toString();
	}

	private static String resolveReplacement(Finding finding) {
		if (finding == null) {
			return DEFAULT_MASK;
		}
		String replacement = finding.getReplacement();
		if (replacement == null) {
			return DEFAULT_MASK;
		}
		return replacement;
	}

}
