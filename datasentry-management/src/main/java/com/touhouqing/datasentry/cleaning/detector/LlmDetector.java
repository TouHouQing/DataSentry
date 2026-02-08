package com.touhouqing.datasentry.cleaning.detector;

import com.touhouqing.datasentry.cleaning.model.CleaningLlmOutput;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.prompt.PromptConstant;
import com.touhouqing.datasentry.service.llm.LlmService;
import com.touhouqing.datasentry.util.JsonUtil;
import com.touhouqing.datasentry.util.MarkdownParserUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmDetector {

	private final LlmService llmService;

	public List<Finding> detect(String text, String customPrompt) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		String systemPrompt;
		if (customPrompt != null && !customPrompt.isBlank()) {
			systemPrompt = customPrompt;
		}
		else {
			systemPrompt = PromptConstant.getCleaningDetectPromptTemplate().render(Map.of());
		}
		Flux<ChatResponse> responseFlux = llmService.call(systemPrompt, text);
		String content = llmService.toStringFlux(responseFlux)
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString)
			.block();
		if (content == null || content.isBlank()) {
			return List.of();
		}
		String cleaned = MarkdownParserUtil.extractRawText(content).trim();
		try {
			CleaningLlmOutput output = JsonUtil.getObjectMapper().readValue(cleaned, CleaningLlmOutput.class);
			List<Finding> findings = output.getFindings() != null ? output.getFindings() : new ArrayList<>();
			for (Finding finding : findings) {
				if (finding.getDetectorSource() == null) {
					finding.setDetectorSource("L3_LLM");
				}
			}
			return findings;
		}
		catch (JsonProcessingException e) {
			log.warn("Failed to parse LLM output: {}", cleaned, e);
			return List.of();
		}
	}

}
