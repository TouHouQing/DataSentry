package com.touhouqing.datasentry.cleaning.detector;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.touhouqing.datasentry.cleaning.model.CleaningLlmOutput;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.prompt.PromptLoader;
import com.touhouqing.datasentry.service.aimodelconfig.AiModelRegistry;
import com.touhouqing.datasentry.service.llm.LlmService;
import com.touhouqing.datasentry.util.JsonParseUtil;
import com.touhouqing.datasentry.util.JsonUtil;
import com.touhouqing.datasentry.util.MarkdownParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmDetector {

	private static final String AGENT_OUTPUT_KEY = "cleaning_structured_output";

	private static final AtomicBoolean AGENT_OUTPUTTYPE_UNAVAILABLE_LOGGED = new AtomicBoolean(false);

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	private final AiModelRegistry aiModelRegistry;

	public List<Finding> detect(String text, String customPrompt) {
		return detectStructured(text, customPrompt).findings();
	}

	public LlmDetectResult detectStructured(String text, String customPrompt) {
		if (text == null || text.isBlank()) {
			return LlmDetectResult.success(List.of(), false, "EMPTY_INPUT");
		}
		String systemPrompt = composeSystemPrompt(customPrompt);
		StructuredAttempt agentAttempt = tryAgentOutputType(systemPrompt, text);
		if (agentAttempt.parseSuccess()) {
			return buildSuccessFromAttempt(agentAttempt, text.length());
		}
		logStructuredFallback("AGENT_OUTPUTTYPE", "CHAT_ENTITY", agentAttempt.errorCode());

		StructuredAttempt entityAttempt = tryChatEntity(systemPrompt, text);
		if (entityAttempt.parseSuccess()) {
			return buildSuccessFromAttempt(entityAttempt, text.length());
		}
		logStructuredFallback("CHAT_ENTITY", "RAW_JSON", entityAttempt.errorCode());

		StructuredAttempt rawAttempt = tryRawJson(systemPrompt, text);
		if (rawAttempt.parseSuccess()) {
			return buildSuccessFromAttempt(rawAttempt, text.length());
		}

		String finalErrorCode = firstNonBlank(rawAttempt.errorCode(), entityAttempt.errorCode(),
				agentAttempt.errorCode(), "L3_STRUCTURED_OUTPUT_FAILED");
		String finalMode = firstNonBlank(rawAttempt.mode(), entityAttempt.mode(), agentAttempt.mode(),
				"L3_STRUCTURED_OUTPUT_FAILED");
		String finalRawOutput = firstNonBlank(rawAttempt.rawOutput(), entityAttempt.rawOutput(),
				agentAttempt.rawOutput(), null);
		return LlmDetectResult.failure(finalErrorCode, finalRawOutput, finalMode);
	}

	private LlmDetectResult buildSuccessFromAttempt(StructuredAttempt attempt, int textLength) {
		CleaningLlmOutput output = attempt.output();
		if (output == null) {
			return LlmDetectResult.failure("L3_STRUCTURED_OUTPUT_EMPTY", attempt.rawOutput(),
					firstNonBlank(attempt.mode(), "L3_STRUCTURED_OUTPUT_EMPTY"));
		}
		try {
			String structuredJson = JsonUtil.getObjectMapper().writeValueAsString(output);
			List<Finding> findings = normalizeFindings(structuredJson, textLength);
			return LlmDetectResult.success(findings, attempt.repaired(), attempt.mode());
		}
		catch (JsonProcessingException e) {
			log.warn("Failed to normalize structured output", e);
			return LlmDetectResult.failure("L3_STRUCTURED_OUTPUT_NORMALIZE_FAILED", attempt.rawOutput(),
					firstNonBlank(attempt.mode(), "L3_STRUCTURED_OUTPUT_NORMALIZE_FAILED"));
		}
	}

	private String composeSystemPrompt(String customPrompt) {
		String basePrompt = PromptLoader.loadPrompt("cleaning-detect");
		if (customPrompt == null || customPrompt.isBlank()) {
			return basePrompt;
		}
		return basePrompt + "\n\n# Rule-Specific Instructions\n" + customPrompt.trim();
	}

	protected StructuredAttempt tryAgentOutputType(String systemPrompt, String text) {
		if (aiModelRegistry == null) {
			return StructuredAttempt.failure("AGENT_OUTPUTTYPE_UNAVAILABLE", null, "AGENT_OUTPUTTYPE_UNAVAILABLE");
		}
		ChatModel chatModel = aiModelRegistry.getChatModel();
		if (chatModel == null) {
			return StructuredAttempt.failure("AGENT_OUTPUTTYPE_UNAVAILABLE", null, "AGENT_OUTPUTTYPE_UNAVAILABLE");
		}
		try {
			ReactAgent agent = ReactAgent.builder()
				.name("cleaning_l3")
				.model(chatModel)
				.instruction(systemPrompt)
				.outputType(CleaningLlmOutput.class)
				.outputKey(AGENT_OUTPUT_KEY)
				.build();

			Optional<NodeOutput> optionalNodeOutput = agent.invokeAndGetOutput(text);
			if (optionalNodeOutput.isPresent()) {
				CleaningLlmOutput outputFromNode = extractStructuredOutput(optionalNodeOutput.get());
				if (outputFromNode != null) {
					return StructuredAttempt.success(outputFromNode, false, "AGENT_OUTPUTTYPE");
				}
			}

			AssistantMessage assistantMessage = agent.call(text);
			CleaningLlmOutput outputFromMessage = parseCandidateAsOutput(assistantMessage, false);
			if (outputFromMessage != null) {
				return StructuredAttempt.success(outputFromMessage, false, "AGENT_OUTPUTTYPE");
			}
			String rawText = assistantMessage != null ? assistantMessage.getText() : null;
			return StructuredAttempt.failure("AGENT_OUTPUTTYPE_PARSE_FAILED", rawText, "AGENT_OUTPUTTYPE_PARSE_FAILED");
		}
		catch (NoClassDefFoundError e) {
			logAgentOutputTypeUnavailable(e.getMessage());
			return StructuredAttempt.failure("AGENT_OUTPUTTYPE_UNAVAILABLE", null, "AGENT_OUTPUTTYPE_UNAVAILABLE");
		}
		catch (Exception e) {
			log.warn("Agent outputType call failed: {}", e.getMessage());
			return StructuredAttempt.failure("AGENT_OUTPUTTYPE_CALL_FAILED", null, "AGENT_OUTPUTTYPE_CALL_FAILED");
		}
	}

	protected StructuredAttempt tryChatEntity(String systemPrompt, String text) {
		if (llmService == null) {
			return StructuredAttempt.failure("CHAT_ENTITY_UNAVAILABLE", null, "CHAT_ENTITY_UNAVAILABLE");
		}
		try {
			CleaningLlmOutput output = llmService.callForEntity(systemPrompt, text, null, CleaningLlmOutput.class);
			if (output == null) {
				return StructuredAttempt.failure("CHAT_ENTITY_EMPTY", null, "CHAT_ENTITY_EMPTY");
			}
			return StructuredAttempt.success(output, false, "CHAT_ENTITY");
		}
		catch (UnsupportedOperationException e) {
			return StructuredAttempt.failure("CHAT_ENTITY_UNAVAILABLE", null, "CHAT_ENTITY_UNAVAILABLE");
		}
		catch (Exception e) {
			log.warn("Structured entity call failed: {}", e.getMessage());
			return StructuredAttempt.failure("CHAT_ENTITY_CALL_FAILED", null, "CHAT_ENTITY_CALL_FAILED");
		}
	}

	protected StructuredAttempt tryRawJson(String systemPrompt, String text) {
		if (llmService == null) {
			return StructuredAttempt.failure("RAW_JSON_UNAVAILABLE", null, "RAW_JSON_UNAVAILABLE");
		}
		try {
			String rawOutput = llmService.toStringFlux(llmService.call(systemPrompt, text))
				.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block();
			if (rawOutput == null || rawOutput.isBlank()) {
				return StructuredAttempt.failure("RAW_JSON_EMPTY", rawOutput, "RAW_JSON_EMPTY");
			}
			CleaningLlmOutput parsed = parseTextCandidate(rawOutput, false);
			if (parsed != null) {
				return StructuredAttempt.success(parsed, false, "RAW_JSON");
			}
			CleaningLlmOutput repaired = parseTextCandidate(rawOutput, true);
			if (repaired != null) {
				return StructuredAttempt.success(repaired, true, "RAW_JSON_REPAIRED");
			}
			return StructuredAttempt.failure("RAW_JSON_PARSE_FAILED", rawOutput, "RAW_JSON_PARSE_FAILED");
		}
		catch (Exception e) {
			log.warn("Raw JSON fallback call failed: {}", e.getMessage());
			return StructuredAttempt.failure("RAW_JSON_CALL_FAILED", null, "RAW_JSON_CALL_FAILED");
		}
	}

	private void logStructuredFallback(String fromMode, String toMode, String reason) {
		log.info("event=L3_STRUCTURED_FALLBACK from={} to={} reason={}", fromMode, toMode,
				firstNonBlank(reason, "UNKNOWN"));
	}

	private CleaningLlmOutput extractStructuredOutput(NodeOutput nodeOutput) {
		if (nodeOutput == null) {
			return null;
		}
		OverAllState state = nodeOutput.state();
		if (state != null) {
			Object structuredCandidate = state.value(AGENT_OUTPUT_KEY).orElse(null);
			CleaningLlmOutput output = parseCandidateAsOutput(structuredCandidate, false);
			if (output != null) {
				return output;
			}
			Object messages = state.value("messages").orElse(null);
			output = parseCandidateAsOutput(messages, false);
			if (output != null) {
				return output;
			}
		}
		return parseCandidateAsOutput(nodeOutput, false);
	}

	private CleaningLlmOutput parseCandidateAsOutput(Object candidate, boolean allowRepair) {
		if (candidate == null) {
			return null;
		}
		if (candidate instanceof CleaningLlmOutput output) {
			return output;
		}
		if (candidate instanceof AssistantMessage assistantMessage) {
			return parseTextCandidate(assistantMessage.getText(), allowRepair);
		}
		if (candidate instanceof String textCandidate) {
			return parseTextCandidate(textCandidate, allowRepair);
		}
		if (candidate instanceof Map<?, ?> mapCandidate) {
			return parseMapCandidate(mapCandidate);
		}
		if (candidate instanceof List<?> listCandidate && !listCandidate.isEmpty()) {
			return parseCandidateAsOutput(listCandidate.get(listCandidate.size() - 1), allowRepair);
		}
		String assistantText = extractAssistantText(candidate);
		if (assistantText != null && !assistantText.isBlank()) {
			return parseTextCandidate(assistantText, allowRepair);
		}
		JsonNode tree = JsonUtil.getObjectMapper().valueToTree(candidate);
		return parseStructuredNode(tree);
	}

	private CleaningLlmOutput parseMapCandidate(Map<?, ?> mapCandidate) {
		JsonNode node = JsonUtil.getObjectMapper().valueToTree(mapCandidate);
		return parseStructuredNode(node);
	}

	private CleaningLlmOutput parseTextCandidate(String textCandidate, boolean allowRepair) {
		String cleaned = MarkdownParserUtil.extractRawText(textCandidate).trim();
		if (cleaned.isBlank()) {
			return null;
		}
		try {
			JsonNode node = JsonUtil.getObjectMapper().readTree(cleaned);
			return parseStructuredNode(node);
		}
		catch (Exception e) {
			if (!allowRepair || jsonParseUtil == null) {
				return null;
			}
			try {
				return jsonParseUtil.tryConvertToObject(cleaned, CleaningLlmOutput.class);
			}
			catch (Exception ignored) {
				return null;
			}
		}
	}

	private CleaningLlmOutput parseStructuredNode(JsonNode node) {
		if (node == null || !node.isObject()) {
			return null;
		}
		JsonNode findingsNode = node.get("findings");
		if (findingsNode == null || !findingsNode.isArray()) {
			return null;
		}
		try {
			return JsonUtil.getObjectMapper().treeToValue(node, CleaningLlmOutput.class);
		}
		catch (JsonProcessingException e) {
			return null;
		}
	}

	private void logAgentOutputTypeUnavailable(String reason) {
		if (AGENT_OUTPUTTYPE_UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
			log.warn("Agent outputType unavailable, will fallback to CHAT_ENTITY. reason={}", reason);
		}
	}

	private String extractAssistantText(Object assistantMessage) {
		if (assistantMessage == null) {
			return null;
		}
		try {
			Method textMethod = assistantMessage.getClass().getMethod("getText");
			Object text = textMethod.invoke(assistantMessage);
			if (text instanceof String result) {
				return result;
			}
		}
		catch (Exception ignored) {
			return null;
		}
		return null;
	}

	private List<Finding> normalizeFindings(String json, int textLength) throws JsonProcessingException {
		CleaningLlmOutput output = JsonUtil.getObjectMapper().readValue(json, CleaningLlmOutput.class);
		if (output == null) {
			return List.of();
		}
		List<Finding> source = output.getFindings() != null ? output.getFindings() : new ArrayList<>();
		List<Finding> normalized = new ArrayList<>();
		for (Finding finding : source) {
			if (!isValidFinding(finding, textLength)) {
				continue;
			}
			if (finding.getDetectorSource() == null || finding.getDetectorSource().isBlank()) {
				finding.setDetectorSource("L3_LLM");
			}
			normalized.add(finding);
		}
		return normalized;
	}

	private boolean isValidFinding(Finding finding, int textLength) {
		if (finding == null) {
			return false;
		}
		Double severity = finding.getSeverity();
		if (severity != null && (severity < 0 || severity > 1)) {
			return false;
		}
		Integer start = finding.getStart();
		Integer end = finding.getEnd();
		if (start == null && end == null) {
			return true;
		}
		if (start == null || end == null) {
			return false;
		}
		return start >= 0 && end > start && end <= textLength;
	}

	private String firstNonBlank(String first, String fallback) {
		return firstNonBlank(first, null, null, fallback);
	}

	private String firstNonBlank(String first, String second, String third, String fallback) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		if (third != null && !third.isBlank()) {
			return third;
		}
		return fallback;
	}

	public record StructuredAttempt(CleaningLlmOutput output, boolean parseSuccess, boolean repaired, String mode,
			String errorCode, String rawOutput) {

		public static StructuredAttempt success(CleaningLlmOutput output, boolean repaired, String mode) {
			return new StructuredAttempt(output, true, repaired, mode != null ? mode : "UNKNOWN", null, null);
		}

		public static StructuredAttempt failure(String errorCode, String rawOutput, String mode) {
			String resolvedMode = mode != null ? mode : (errorCode != null ? errorCode : "FAILED");
			return new StructuredAttempt(null, false, false, resolvedMode, errorCode, rawOutput);
		}

	}

	public record LlmDetectResult(List<Finding> findings, boolean parseSuccess, boolean repaired, String errorCode,
			String rawOutput, String mode) {

		public static LlmDetectResult success(List<Finding> findings, boolean repaired) {
			return success(findings, repaired, repaired ? "RAW_JSON_REPAIRED" : "UNKNOWN");
		}

		public static LlmDetectResult success(List<Finding> findings, boolean repaired, String mode) {
			List<Finding> safeFindings = findings != null ? findings : List.of();
			return new LlmDetectResult(safeFindings, true, repaired, null, null, mode != null ? mode : "UNKNOWN");
		}

		public static LlmDetectResult failure(String errorCode, String rawOutput) {
			return failure(errorCode, rawOutput, errorCode != null ? errorCode : "FAILED");
		}

		public static LlmDetectResult failure(String errorCode, String rawOutput, String mode) {
			return new LlmDetectResult(List.of(), false, false, errorCode, rawOutput,
					mode != null ? mode : (errorCode != null ? errorCode : "FAILED"));
		}

	}

}
