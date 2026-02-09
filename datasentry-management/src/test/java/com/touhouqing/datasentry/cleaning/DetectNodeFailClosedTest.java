package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.detector.L2Detector;
import com.touhouqing.datasentry.cleaning.detector.LlmDetector;
import com.touhouqing.datasentry.cleaning.detector.RegexDetector;
import com.touhouqing.datasentry.cleaning.model.CleaningContext;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicySnapshot;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.pipeline.DetectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DetectNodeFailClosedTest {

	@Test
	public void shouldSkipL3WhenDisableL3FlagTrue() {
		RegexDetector regexDetector = new StubRegexDetector(List.of());
		L2Detector l2Detector = new StubL2Detector(List.of());
		StubLlmDetector llmDetector = new StubLlmDetector(Map.of());
		DetectNode node = new DetectNode(regexDetector, l2Detector, llmDetector);

		CleaningPolicySnapshot snapshot = CleaningPolicySnapshot.builder()
			.config(CleaningPolicyConfig.builder().llmEnabled(true).build())
			.rules(List.of(CleaningRule.builder().ruleType("LLM").build(),
					CleaningRule.builder().ruleType("REGEX").build()))
			.build();
		CleaningContext context = CleaningContext.builder().originalText("abc").policySnapshot(snapshot).build();
		context.getMetadata().put("disableL3", true);

		node.process(context);

		assertEquals(0, llmDetector.calledPrompts.size());
		assertEquals(List.of(), context.getFindings());
	}

	@Test
	public void shouldCallMultipleL3RulesByOrderAndAggregateFindings() {
		RegexDetector regexDetector = new StubRegexDetector(List.of());
		L2Detector l2Detector = new StubL2Detector(List.of());
		Map<String, LlmDetector.LlmDetectResult> resultMap = new HashMap<>();
		resultMap.put("p100", LlmDetector.LlmDetectResult.success(List.of(Finding.builder().category("A").build()),
				false, "AGENT_OUTPUTTYPE"));
		resultMap.put("p50",
				LlmDetector.LlmDetectResult.success(
						List.of(Finding.builder().category("B").build(), Finding.builder().category("C").build()),
						false, "CHAT_ENTITY"));
		StubLlmDetector llmDetector = new StubLlmDetector(resultMap);
		DetectNode node = new DetectNode(regexDetector, l2Detector, llmDetector);

		CleaningPolicySnapshot snapshot = CleaningPolicySnapshot.builder()
			.config(CleaningPolicyConfig.builder().llmEnabled(true).build())
			.rules(List.of(CleaningRule.builder().ruleType("LLM").configJson("{\"prompt\":\"p100\"}").build(),
					CleaningRule.builder().ruleType("LLM").configJson("{\"prompt\":\"p50\"}").build()))
			.build();
		CleaningContext context = CleaningContext.builder().originalText("abc").policySnapshot(snapshot).build();

		node.process(context);

		assertEquals(List.of("p100", "p50"), llmDetector.calledPrompts);
		assertEquals(3, context.getFindings().size());
		assertFalse((Boolean) context.getMetadata().get("l3AllParseFailed"));
		assertEquals(2, context.getMetrics().get("l3ParseSuccessCount"));
		assertEquals(0, context.getMetrics().get("l3ParseFailCount"));
		@SuppressWarnings("unchecked")
		Map<String, Integer> modeCounts = (Map<String, Integer>) context.getMetrics().get("l3ModeCounts");
		assertEquals(1, modeCounts.get("AGENT_OUTPUTTYPE"));
		assertEquals(1, modeCounts.get("CHAT_ENTITY"));
	}

	@Test
	public void shouldMarkL3AllParseFailedWhenAllRulesFail() {
		RegexDetector regexDetector = new StubRegexDetector(List.of());
		L2Detector l2Detector = new StubL2Detector(List.of());
		Map<String, LlmDetector.LlmDetectResult> resultMap = new HashMap<>();
		resultMap.put("p100", LlmDetector.LlmDetectResult.failure("AGENT_OUTPUTTYPE_UNAVAILABLE", null,
				"AGENT_OUTPUTTYPE_UNAVAILABLE"));
		resultMap.put("p50",
				LlmDetector.LlmDetectResult.failure("CHAT_ENTITY_CALL_FAILED", null, "CHAT_ENTITY_CALL_FAILED"));
		StubLlmDetector llmDetector = new StubLlmDetector(resultMap);
		DetectNode node = new DetectNode(regexDetector, l2Detector, llmDetector);

		CleaningPolicySnapshot snapshot = CleaningPolicySnapshot.builder()
			.config(CleaningPolicyConfig.builder().llmEnabled(true).build())
			.rules(List.of(CleaningRule.builder().ruleType("LLM").configJson("{\"prompt\":\"p100\"}").build(),
					CleaningRule.builder().ruleType("LLM").configJson("{\"prompt\":\"p50\"}").build()))
			.build();
		CleaningContext context = CleaningContext.builder().originalText("abc").policySnapshot(snapshot).build();

		node.process(context);

		assertTrue((Boolean) context.getMetadata().get("l3AllParseFailed"));
		assertEquals(0, context.getFindings().size());
		assertEquals(0, context.getMetrics().get("l3ParseSuccessCount"));
		assertEquals(2, context.getMetrics().get("l3ParseFailCount"));
	}

	@Test
	public void shouldNotMarkParseFailedWhenStructuredOutputIsEmptyButValid() {
		RegexDetector regexDetector = new StubRegexDetector(List.of());
		L2Detector l2Detector = new StubL2Detector(List.of());
		Map<String, LlmDetector.LlmDetectResult> resultMap = new HashMap<>();
		resultMap.put("p100", LlmDetector.LlmDetectResult.success(List.of(), false, "AGENT_OUTPUTTYPE"));
		resultMap.put("p50", LlmDetector.LlmDetectResult.success(List.of(), false, "CHAT_ENTITY"));
		StubLlmDetector llmDetector = new StubLlmDetector(resultMap);
		DetectNode node = new DetectNode(regexDetector, l2Detector, llmDetector);

		CleaningPolicySnapshot snapshot = CleaningPolicySnapshot.builder()
			.config(CleaningPolicyConfig.builder().llmEnabled(true).build())
			.rules(List.of(CleaningRule.builder().ruleType("LLM").configJson("{\"prompt\":\"p100\"}").build(),
					CleaningRule.builder().ruleType("LLM").configJson("{\"prompt\":\"p50\"}").build()))
			.build();
		CleaningContext context = CleaningContext.builder().originalText("abc").policySnapshot(snapshot).build();

		node.process(context);

		assertFalse((Boolean) context.getMetadata().get("l3AllParseFailed"));
		assertEquals(0, context.getFindings().size());
		assertEquals(2, context.getMetrics().get("l3EmptyStructuredCount"));
	}

	private static class StubRegexDetector extends RegexDetector {

		private final List<Finding> findings;

		StubRegexDetector(List<Finding> findings) {
			this.findings = findings;
		}

		@Override
		public List<Finding> detect(String text, CleaningRule rule) {
			return findings;
		}

	}

	private static class StubL2Detector extends L2Detector {

		private final List<Finding> findings;

		StubL2Detector(List<Finding> findings) {
			super(null);
			this.findings = findings;
		}

		@Override
		public List<Finding> detect(String text, CleaningRule rule, CleaningPolicyConfig config) {
			return findings;
		}

	}

	private static class StubLlmDetector extends LlmDetector {

		private final Map<String, LlmDetectResult> resultMap;

		private final List<String> calledPrompts = new ArrayList<>();

		StubLlmDetector(Map<String, LlmDetectResult> resultMap) {
			super(null, null, null);
			this.resultMap = resultMap;
		}

		@Override
		public LlmDetectResult detectStructured(String text, String customPrompt) {
			calledPrompts.add(customPrompt);
			return resultMap.getOrDefault(customPrompt, LlmDetectResult.success(List.of(), false, "AGENT_OUTPUTTYPE"));
		}

	}

}
