package com.touhouqing.datasentry.cleaning.detector;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.touhouqing.datasentry.cleaning.context.AiCostContextHolder;
import com.touhouqing.datasentry.cleaning.model.CleaningLlmOutput;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.properties.DataSentryProperties;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmDetector {

	private static final String AGENT_OUTPUT_KEY = "cleaning_structured_output";

	private static final AtomicBoolean AGENT_OUTPUTTYPE_UNAVAILABLE_LOGGED = new AtomicBoolean(false);

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	private final AiModelRegistry aiModelRegistry;

	private final DataSentryProperties dataSentryProperties;

	private final Map<String, ProviderCapability> providerCapabilityCache = new ConcurrentHashMap<>();

	public List<Finding> detect(String text, String customPrompt) {
		return detectStructured(text, customPrompt).findings();
	}

	public LlmDetectResult detectStructured(String text, String customPrompt) {
		if (text == null || text.isBlank()) {
			return LlmDetectResult.success(List.of(), false, "EMPTY_INPUT");
		}
		String systemPrompt = composeSystemPrompt(customPrompt);
		String provider = resolveCurrentProvider();
		L3Strategy strategy = resolveStrategy();
		List<AttemptMode> attemptOrder = resolveAttemptOrder(strategy, provider);
		if (attemptOrder.isEmpty()) {
			return LlmDetectResult.failure("L3_ATTEMPT_DISABLED", null, "L3_ATTEMPT_DISABLED");
		}
		StructuredAttempt lastAttempt = null;
		for (int index = 0; index < attemptOrder.size(); index++) {
			AttemptMode mode = attemptOrder.get(index);
			StructuredAttempt attempt = executeAttemptWithTimeout(mode, () -> executeAttempt(mode, systemPrompt, text));
			lastAttempt = attempt;
			if (attempt.parseSuccess()) {
				updateProviderCapability(provider, mode);
				return buildSuccessFromAttempt(attempt, text.length());
			}
			if (index < attemptOrder.size() - 1) {
				logStructuredFallback(mode.name(), attemptOrder.get(index + 1).name(), attempt.errorCode());
			}
		}
		if (lastAttempt == null) {
			return LlmDetectResult.failure("L3_STRUCTURED_OUTPUT_FAILED", null, "L3_STRUCTURED_OUTPUT_FAILED");
		}
		String finalErrorCode = firstNonBlank(lastAttempt.errorCode(), "L3_STRUCTURED_OUTPUT_FAILED");
		String finalMode = firstNonBlank(lastAttempt.mode(), "L3_STRUCTURED_OUTPUT_FAILED");
		return LlmDetectResult.failure(finalErrorCode, lastAttempt.rawOutput(), finalMode);
	}

	public BatchDetectResult detectStructuredBatch(List<BatchInput> inputs, String customPrompt) {
		if (inputs == null || inputs.isEmpty()) {
			return BatchDetectResult.success(Map.of(), "BATCH_EMPTY");
		}
		String systemPrompt = composeSystemPrompt(customPrompt) + "\n\n" + "# Batch Output Instructions\n"
				+ "Return JSON ONLY with shape: {\"items\":[{\"itemId\":\"...\",\"findings\":[...]}]}\n"
				+ "- itemId must match input itemId exactly\n" + "- include every input item exactly once\n"
				+ "- if no risk for an item, use findings: []\n" + "- no markdown code block\n\n"
				+ "Batch Example:\n" + "Input items: [{\"itemId\":\"1\",\"text\":\"hello\"}, {\"itemId\":\"2\",\"text\":\"drop table\"}]\n"
				+ "Output: {\"items\": [{\"itemId\":\"1\",\"findings\":[]}, {\"itemId\":\"2\",\"findings\":[{\"type\":\"DESTRUCTIVE_OPERATION\",\"severity\":0.9}]}]}";
		String userPrompt = buildBatchUserPrompt(inputs);
		BatchCallResult callResult = executeBatchCallWithTimeout(() -> callBatchRawJson(systemPrompt, userPrompt));
		if (!callResult.success()) {
			return buildBatchFailure(inputs, callResult.errorCode(), callResult.mode());
		}
		Map<String, LlmDetectResult> parsed = parseBatchRawOutput(callResult.rawOutput(), inputs);
		if (parsed == null) {
			return buildBatchFailure(inputs, "L3_BATCH_PARSE_FAILED", "L3_BATCH_PARSE_FAILED");
		}
		Map<String, LlmDetectResult> completed = new LinkedHashMap<>();
		for (BatchInput input : inputs) {
			LlmDetectResult itemResult = parsed.get(input.itemId());
			if (itemResult == null) {
				itemResult = LlmDetectResult.failure("L3_BATCH_MISSING_ITEM", null, "L3_BATCH_MISSING_ITEM");
			}
			completed.put(input.itemId(), itemResult);
		}
		return BatchDetectResult.success(completed, "RAW_JSON_BATCH");
	}

	private StructuredAttempt executeAttempt(AttemptMode mode, String systemPrompt, String text) {
		if (mode == AttemptMode.AGENT_OUTPUTTYPE) {
			return tryAgentOutputType(systemPrompt, text);
		}
		if (mode == AttemptMode.CHAT_ENTITY) {
			return tryChatEntity(systemPrompt, text);
		}
		return tryRawJson(systemPrompt, text);
	}

	private BatchCallResult executeBatchCallWithTimeout(Supplier<String> callSupplier) {
		int timeoutMs = resolveBatchTimeoutMs();
		if (timeoutMs <= 0) {
			try {
				return BatchCallResult.success(callSupplier.get(), "RAW_JSON_BATCH");
			}
			catch (Exception e) {
				return BatchCallResult.failure("L3_BATCH_CALL_FAILED", "L3_BATCH_CALL_FAILED");
			}
		}
		AiCostContextHolder.RequestContext capturedContext = AiCostContextHolder.getContext();
		CompletableFuture<String> future = CompletableFuture
			.supplyAsync(() -> executeStringWithCapturedContext(callSupplier, capturedContext));
		try {
			String raw = future.get(timeoutMs, TimeUnit.MILLISECONDS);
			return BatchCallResult.success(raw, "RAW_JSON_BATCH");
		}
		catch (TimeoutException e) {
			future.cancel(true);
			log.warn("L3 batch timeout timeoutMs={}", timeoutMs);
			return BatchCallResult.failure("L3_BATCH_TIMEOUT", "L3_BATCH_TIMEOUT");
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return BatchCallResult.failure("L3_BATCH_INTERRUPTED", "L3_BATCH_INTERRUPTED");
		}
		catch (ExecutionException e) {
			return BatchCallResult.failure("L3_BATCH_CALL_FAILED", "L3_BATCH_CALL_FAILED");
		}
	}

	private StructuredAttempt executeAttemptWithTimeout(AttemptMode mode, Supplier<StructuredAttempt> attemptSupplier) {
		int timeoutMs = resolveAttemptTimeoutMs();
		if (timeoutMs <= 0) {
			return attemptSupplier.get();
		}
		AiCostContextHolder.RequestContext capturedContext = AiCostContextHolder.getContext();
		CompletableFuture<StructuredAttempt> future = CompletableFuture
			.supplyAsync(() -> executeWithCapturedContext(attemptSupplier, capturedContext));
		try {
			return future.get(timeoutMs, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException e) {
			future.cancel(true);
			String timeoutCode = mode.name() + "_TIMEOUT";
			log.warn("L3 attempt timeout mode={} timeoutMs={}", mode, timeoutMs);
			return StructuredAttempt.failure(timeoutCode, null, timeoutCode);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			String interruptCode = mode.name() + "_INTERRUPTED";
			return StructuredAttempt.failure(interruptCode, null, interruptCode);
		}
		catch (ExecutionException e) {
			String failedCode = mode.name() + "_CALL_FAILED";
			return StructuredAttempt.failure(failedCode, null, failedCode);
		}
	}

	private StructuredAttempt executeWithCapturedContext(Supplier<StructuredAttempt> attemptSupplier,
			AiCostContextHolder.RequestContext capturedContext) {
		AiCostContextHolder.RequestContext previousContext = AiCostContextHolder.getContext();
		try {
			if (capturedContext != null) {
				AiCostContextHolder.setContext(capturedContext.threadId(), capturedContext.agentId());
			}
			else {
				AiCostContextHolder.clearContext();
			}
			return attemptSupplier.get();
		}
		finally {
			if (previousContext != null) {
				AiCostContextHolder.setContext(previousContext.threadId(), previousContext.agentId());
			}
			else {
				AiCostContextHolder.clearContext();
			}
		}
	}

	private String executeStringWithCapturedContext(Supplier<String> supplier,
			AiCostContextHolder.RequestContext capturedContext) {
		AiCostContextHolder.RequestContext previousContext = AiCostContextHolder.getContext();
		try {
			if (capturedContext != null) {
				AiCostContextHolder.setContext(capturedContext.threadId(), capturedContext.agentId());
			}
			else {
				AiCostContextHolder.clearContext();
			}
			return supplier.get();
		}
		finally {
			if (previousContext != null) {
				AiCostContextHolder.setContext(previousContext.threadId(), previousContext.agentId());
			}
			else {
				AiCostContextHolder.clearContext();
			}
		}
	}

	private String resolveCurrentProvider() {
		if (aiModelRegistry == null) {
			return "unknown";
		}
		try {
			String provider = aiModelRegistry.getCurrentChatProvider();
			if (provider == null || provider.isBlank()) {
				return "unknown";
			}
			return provider.trim().toLowerCase(Locale.ROOT);
		}
		catch (Exception e) {
			return "unknown";
		}
	}

	private L3Strategy resolveStrategy() {
		DataSentryProperties.Cleaning.L3 config = resolveL3Config();
		String strategy = config.getStrategy();
		if (strategy == null || strategy.isBlank()) {
			return L3Strategy.BALANCED;
		}
		try {
			return L3Strategy.valueOf(strategy.trim().toUpperCase(Locale.ROOT));
		}
		catch (Exception e) {
			return L3Strategy.BALANCED;
		}
	}

	private int resolveAttemptTimeoutMs() {
		DataSentryProperties.Cleaning.L3 config = resolveL3Config();
		return Math.max(config.getAttemptTimeoutMs(), 0);
	}

	private int resolveBatchTimeoutMs() {
		DataSentryProperties.Cleaning.L3 config = resolveL3Config();
		return Math.max(config.getBatchTimeoutMs(), 0);
	}

	private int resolveBatchMaxTextLength() {
		DataSentryProperties.Cleaning.L3 config = resolveL3Config();
		return Math.max(config.getBatchMaxTextLength(), 32);
	}

	private int resolveBatchMaxPromptChars() {
		DataSentryProperties.Cleaning.L3 config = resolveL3Config();
		return Math.max(config.getBatchMaxPromptChars(), 512);
	}

	private List<AttemptMode> resolveAttemptOrder(L3Strategy strategy, String provider) {
		List<AttemptMode> baseOrder;
		boolean openAiCompatible = isOpenAiCompatibleProvider(provider);
		if (strategy == L3Strategy.FAST) {
			// Fast: Chat Entity -> Raw JSON
			baseOrder = List.of(AttemptMode.CHAT_ENTITY, AttemptMode.RAW_JSON);
		}
		else if (strategy == L3Strategy.ROBUST) {
			// Robust: Chat Entity -> Agent -> Raw JSON
			baseOrder = List.of(AttemptMode.CHAT_ENTITY, AttemptMode.AGENT_OUTPUTTYPE, AttemptMode.RAW_JSON);
		}
		else {
			// Balanced (Default): Chat Entity -> Raw JSON -> Agent
			// We prioritize Chat Entity (structured) and Raw JSON over the heavier Agent
			// loop.
			baseOrder = List.of(AttemptMode.CHAT_ENTITY, AttemptMode.RAW_JSON, AttemptMode.AGENT_OUTPUTTYPE);
		}
		ProviderCapability capability = resolveProviderCapability(provider);
		List<AttemptMode> ordered = new ArrayList<>();
		if (capability != null && capability.preferredMode() != null) {
			ordered.add(capability.preferredMode());
		}
		ordered.addAll(baseOrder);
		List<AttemptMode> distinctModes = new ArrayList<>();
		for (AttemptMode mode : ordered) {
			if (mode == null || distinctModes.contains(mode) || !isAttemptEnabled(mode)) {
				continue;
			}
			distinctModes.add(mode);
		}
		return distinctModes;
	}

	private ProviderCapability resolveProviderCapability(String provider) {
		if (provider == null || provider.isBlank()) {
			return null;
		}
		ProviderCapability capability = providerCapabilityCache.get(provider);
		if (capability == null) {
			return null;
		}
		long ttlMs = Math.max(resolveL3Config().getProviderCapabilityCacheTtlMs(), 0L);
		if (ttlMs == 0L) {
			return capability;
		}
		if (System.currentTimeMillis() - capability.updatedAtMs() > ttlMs) {
			providerCapabilityCache.remove(provider);
			return null;
		}
		return capability;
	}

	private void updateProviderCapability(String provider, AttemptMode preferredMode) {
		if (provider == null || provider.isBlank() || preferredMode == null) {
			return;
		}
		providerCapabilityCache.put(provider, new ProviderCapability(preferredMode, System.currentTimeMillis()));
	}

	private boolean isOpenAiCompatibleProvider(String provider) {
		if (provider == null || provider.isBlank()) {
			return false;
		}
		String normalized = provider.toLowerCase(Locale.ROOT);
		return normalized.contains("openai") || normalized.contains("custom") || normalized.contains("ollama")
				|| normalized.contains("vllm") || normalized.contains("lmstudio") || normalized.contains("local");
	}

	private boolean isAttemptEnabled(AttemptMode mode) {
		DataSentryProperties.Cleaning.L3 config = resolveL3Config();
		if (mode == AttemptMode.AGENT_OUTPUTTYPE) {
			return config.isEnableAgentOutputtype();
		}
		if (mode == AttemptMode.CHAT_ENTITY) {
			return config.isEnableChatEntity();
		}
		return config.isEnableRawJson();
	}

	private DataSentryProperties.Cleaning.L3 resolveL3Config() {
		if (dataSentryProperties == null || dataSentryProperties.getCleaning() == null
				|| dataSentryProperties.getCleaning().getL3() == null) {
			return new DataSentryProperties.Cleaning.L3();
		}
		return dataSentryProperties.getCleaning().getL3();
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
				return StructuredAttempt.failure("AGENT_OUTPUTTYPE_PARSE_FAILED", null,
						"AGENT_OUTPUTTYPE_PARSE_FAILED");
			}
			return StructuredAttempt.failure("AGENT_OUTPUTTYPE_EMPTY", null, "AGENT_OUTPUTTYPE_EMPTY");
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

	protected String callBatchRawJson(String systemPrompt, String userPrompt) {
		if (llmService == null) {
			throw new IllegalStateException("LLM service unavailable");
		}
		String rawOutput = llmService.toStringFlux(llmService.call(systemPrompt, userPrompt))
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString)
			.block();
		if (rawOutput == null || rawOutput.isBlank()) {
			throw new IllegalStateException("L3 batch output empty");
		}
		return rawOutput;
	}

	private String buildBatchUserPrompt(List<BatchInput> inputs) {
		int maxTextLength = resolveBatchMaxTextLength();
		int maxPromptChars = resolveBatchMaxPromptChars();
		List<Map<String, String>> payload = new ArrayList<>();
		for (BatchInput input : inputs) {
			if (input == null || input.itemId() == null || input.itemId().isBlank()) {
				continue;
			}
			String text = input.text() != null ? input.text() : "";
			if (text.length() > maxTextLength) {
				text = text.substring(0, maxTextLength);
			}
			payload.add(Map.of("itemId", input.itemId(), "text", text));
		}
		String prefix = "Process all items and return JSON object: {\"items\": [{\"itemId\":\"...\",\"findings\":[]}]}. "
				+ "Input items: ";
		List<Map<String, String>> boundedPayload = new ArrayList<>(payload);
		while (true) {
			String json = toJsonQuietly(boundedPayload);
			if (prefix.length() + json.length() <= maxPromptChars || boundedPayload.size() <= 1) {
				return prefix + json;
			}
			boundedPayload.remove(boundedPayload.size() - 1);
		}
	}

	private String toJsonQuietly(Object value) {
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(value);
		}
		catch (Exception e) {
			return "[]";
		}
	}

	private Map<String, LlmDetectResult> parseBatchRawOutput(String rawOutput, List<BatchInput> inputs) {
		if (rawOutput == null || rawOutput.isBlank()) {
			return null;
		}
		String cleaned = MarkdownParserUtil.extractRawText(rawOutput).trim();
		if (cleaned.isBlank()) {
			return null;
		}
		Set<String> expectedIds = inputs.stream()
			.filter(input -> input != null && input.itemId() != null)
			.map(BatchInput::itemId)
			.collect(Collectors.toSet());
		try {
			JsonNode root = JsonUtil.getObjectMapper().readTree(cleaned);
			JsonNode itemsNode = resolveBatchItemsNode(root);
			if (itemsNode == null || !itemsNode.isArray()) {
				return null;
			}
			Map<String, LlmDetectResult> results = new HashMap<>();
			for (JsonNode itemNode : itemsNode) {
				String itemId = itemNode.path("itemId").asText(null);
				if (itemId == null || !expectedIds.contains(itemId)) {
					continue;
				}
				JsonNode findingsNode = itemNode.get("findings");
				if (findingsNode == null || !findingsNode.isArray()) {
					results.put(itemId, LlmDetectResult.failure("L3_BATCH_ITEM_INVALID", itemNode.toString(),
							"L3_BATCH_ITEM_INVALID"));
					continue;
				}
				Map<String, Object> normalizedNode = Map.of("findings",
						JsonUtil.getObjectMapper().convertValue(findingsNode, List.class));
				String normalizedJson = JsonUtil.getObjectMapper().writeValueAsString(normalizedNode);
				List<Finding> findings = normalizeFindings(normalizedJson, Integer.MAX_VALUE);
				results.put(itemId, LlmDetectResult.success(findings, false, "RAW_JSON_BATCH"));
			}
			return results;
		}
		catch (Exception e) {
			return null;
		}
	}

	private JsonNode resolveBatchItemsNode(JsonNode root) {
		if (root == null) {
			return null;
		}
		if (root.isArray()) {
			return root;
		}
		if (root.isObject()) {
			JsonNode itemsNode = root.get("items");
			if (itemsNode != null) {
				return itemsNode;
			}
		}
		return null;
	}

	private BatchDetectResult buildBatchFailure(List<BatchInput> inputs, String errorCode, String mode) {
		Map<String, LlmDetectResult> failed = new LinkedHashMap<>();
		for (BatchInput input : inputs) {
			if (input == null || input.itemId() == null) {
				continue;
			}
			failed.put(input.itemId(), LlmDetectResult.failure(errorCode, null, mode));
		}
		return BatchDetectResult.failure(failed, errorCode, mode);
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

	private enum AttemptMode {

		AGENT_OUTPUTTYPE, CHAT_ENTITY, RAW_JSON

	}

	private enum L3Strategy {

		FAST, BALANCED, ROBUST

	}

	private record ProviderCapability(AttemptMode preferredMode, long updatedAtMs) {
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

	public record BatchInput(String itemId, String text) {

	}

	public record BatchDetectResult(Map<String, LlmDetectResult> results, boolean parseSuccess, String errorCode,
			String mode) {

		public static BatchDetectResult success(Map<String, LlmDetectResult> results, String mode) {
			Map<String, LlmDetectResult> safeResults = results != null ? results : Map.of();
			return new BatchDetectResult(safeResults, true, null, mode != null ? mode : "RAW_JSON_BATCH");
		}

		public static BatchDetectResult failure(Map<String, LlmDetectResult> results, String errorCode, String mode) {
			Map<String, LlmDetectResult> safeResults = results != null ? results : Map.of();
			return new BatchDetectResult(safeResults, false, errorCode,
					mode != null ? mode : (errorCode != null ? errorCode : "L3_BATCH_FAILED"));
		}

	}

	private record BatchCallResult(boolean success, String rawOutput, String errorCode, String mode) {

		static BatchCallResult success(String rawOutput, String mode) {
			return new BatchCallResult(true, rawOutput, null, mode);
		}

		static BatchCallResult failure(String errorCode, String mode) {
			return new BatchCallResult(false, null, errorCode, mode);
		}

	}

}
