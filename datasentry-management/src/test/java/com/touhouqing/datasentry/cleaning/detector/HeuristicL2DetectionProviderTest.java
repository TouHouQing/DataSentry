package com.touhouqing.datasentry.cleaning.detector;

import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeuristicL2DetectionProviderTest {

	private final HeuristicL2DetectionProvider provider = new HeuristicL2DetectionProvider();

	@Test
	public void shouldDetectEntropyAnomaly() {
		CleaningRule rule = CleaningRule.builder()
			.category("ANOMALY_ENTROPY")
			.configJson("{\"threshold\": 3.5}")
			.build();

		// High entropy string (random characters with high variety)
		String highEntropyText = "abcdefghijklmnopqrstuvwxyz1234567890!@#$%^&*()_+";
		List<Finding> findings = provider.detect(highEntropyText, rule, null);

		assertEquals(1, findings.size());
		assertEquals("ANOMALY_ENTROPY", findings.get(0).getCategory());
		assertEquals("L2_HEURISTIC_ENTROPY", findings.get(0).getDetectorSource());
	}

	@Test
	public void shouldPassNormalTextForEntropy() {
		CleaningRule rule = CleaningRule.builder()
			.category("ANOMALY_ENTROPY")
			.configJson("{\"threshold\": 4.8}")
			.build();

		// Normal text (lower entropy due to language structure)
		String normalText = "This is a normal English sentence with expected character distribution.";
		List<Finding> findings = provider.detect(normalText, rule, null);

		assertTrue(findings.isEmpty());
	}

	@Test
	public void shouldDetectRepetition() {
		CleaningRule rule = CleaningRule.builder()
			.category("ANOMALY_REPETITION")
			.configJson("{\"maxRepetition\": 5}")
			.build();

		String repeatedText = "This is a test with aaaaaaa repetition";
		List<Finding> findings = provider.detect(repeatedText, rule, null);

		assertEquals(1, findings.size());
		assertEquals("ANOMALY_REPETITION", findings.get(0).getCategory());
		assertEquals("L2_HEURISTIC_REPETITION", findings.get(0).getDetectorSource());
	}

	@Test
	public void shouldPassNormalTextForRepetition() {
		CleaningRule rule = CleaningRule.builder()
			.category("ANOMALY_REPETITION")
			.configJson("{\"maxRepetition\": 10}")
			.build();

		String normalText = "Wooooow that is cool"; // 5 'o's, less than 10
		List<Finding> findings = provider.detect(normalText, rule, null);

		assertTrue(findings.isEmpty());
	}

	@Test
	public void shouldReturnEmptyForUnknownCategory() {
		CleaningRule rule = CleaningRule.builder().category("SPAM").build();
		CleaningPolicyConfig config = CleaningPolicyConfig.builder().l2Threshold(0.5).build();

		String spamText = "兼职转账点击";
		List<Finding> findings = provider.detect(spamText, rule, config);

		assertTrue(findings.isEmpty());
	}

	@Test
	public void shouldDetectL2Regex() {
		CleaningRule rule = CleaningRule.builder()
			.category("L2_REGEX")
			.configJson("{\"pattern\": \"(?i)(代开票|发票|保真)\", \"threshold\": 0.95}")
			.build();

		String riskyText = "需要代开票的请联系";
		List<Finding> findings = provider.detect(riskyText, rule, null);

		assertEquals(1, findings.size());
		Finding finding = findings.get(0);
		assertEquals("L2_REGEX", finding.getCategory());
		assertEquals("L2_REGEX_MATCH", finding.getDetectorSource());
		assertEquals(0.95, finding.getSeverity());
		assertEquals(2, finding.getStart());
		assertEquals(5, finding.getEnd());
	}

	@Test
	public void shouldPassL2RegexForSafeText() {
		CleaningRule rule = CleaningRule.builder()
			.category("L2_REGEX")
			.configJson("{\"pattern\": \"(?i)(代开票|发票|保真)\", \"threshold\": 0.95}")
			.build();

		String safeText = "正常发邮件";
		List<Finding> findings = provider.detect(safeText, rule, null);

		assertTrue(findings.isEmpty());
	}

}
