package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.detector.HeuristicL2DetectionProvider;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeuristicL2DetectionProviderTest {

	private final HeuristicL2DetectionProvider provider = new HeuristicL2DetectionProvider();

	@Test
	public void shouldDetectL2Regex() {
		CleaningRule rule = CleaningRule.builder()
			.id(100L)
			.category("L2_REGEX")
			.configJson("{\"pattern\": \"(?i)(代开票|发票)\", \"threshold\": 0.85}")
			.updatedTime(LocalDateTime.now())
			.build();

		List<Finding> findings = provider.detect("这里有代开票服务", rule, null);

		assertEquals(1, findings.size());
		Finding finding = findings.get(0);
		assertEquals(0.85, finding.getSeverity());
		assertEquals("L2_REGEX_MATCH", finding.getDetectorSource());
		assertEquals(3, finding.getStart()); // "代开票" starts at index 3
	}

	@Test
	public void shouldUpdateCacheWhenRuleUpdates() {
		// Initial Rule
		CleaningRule rule = CleaningRule.builder()
			.id(101L)
			.category("L2_REGEX")
			.configJson("{\"pattern\": \"foo\", \"threshold\": 0.5}")
			.updatedTime(LocalDateTime.now().minusHours(1))
			.build();

		// First detection
		List<Finding> findings1 = provider.detect("foo bar", rule, null);
		assertEquals(1, findings1.size());
		assertEquals(0.5, findings1.get(0).getSeverity());

		// Update Rule (change pattern and threshold)
		rule.setConfigJson("{\"pattern\": \"bar\", \"threshold\": 0.9}");
		rule.setUpdatedTime(LocalDateTime.now());

		// Second detection (should use new pattern/threshold)
		List<Finding> findings2 = provider.detect("foo bar", rule, null);
		assertEquals(1, findings2.size());
		assertEquals(0.9, findings2.get(0).getSeverity());
		assertEquals(4, findings2.get(0).getStart()); // "bar" matches now
	}

}
