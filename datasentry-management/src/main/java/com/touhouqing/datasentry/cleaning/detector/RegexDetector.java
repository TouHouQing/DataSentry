package com.touhouqing.datasentry.cleaning.detector;

import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.model.RegexRuleConfig;
import com.touhouqing.datasentry.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RegexDetector {

	private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

	private static final String DEFAULT_MASK = "[REDACTED]";

	public List<Finding> detect(String text, CleaningRule rule) {
		if (text == null || text.isEmpty() || rule == null || rule.getConfigJson() == null) {
			return List.of();
		}
		RegexRuleConfig config = parseConfig(rule.getConfigJson(), rule.getId());
		if (config == null || config.getPattern() == null || config.getPattern().isBlank()) {
			return List.of();
		}
		String originalPattern = config.getPattern();
		Pattern pattern = compilePattern(originalPattern, config.getFlags());
		if (pattern == null) {
			return List.of();
		}
		String replacement = resolveReplacement(config);
		List<Finding> findings = detectWithCompiledPattern(text, rule, pattern, replacement);
		String effectivePattern = originalPattern;
		boolean normalizedApplied = false;
		if (findings.isEmpty()) {
			String normalizedPattern = normalizeOverEscapedPattern(originalPattern);
			if (!normalizedPattern.equals(originalPattern)) {
				Pattern normalizedCompiled = compilePattern(normalizedPattern, config.getFlags());
				if (normalizedCompiled != null) {
					List<Finding> normalizedFindings = detectWithCompiledPattern(text, rule, normalizedCompiled,
							replacement);
					if (!normalizedFindings.isEmpty()) {
						findings = normalizedFindings;
						effectivePattern = normalizedPattern;
						normalizedApplied = true;
						log.warn("Regex pattern auto-normalized for ruleId={} originalPattern={} normalizedPattern={}",
								rule.getId(), originalPattern, normalizedPattern);
					}
				}
			}
		}
		log.info(
				"Regex detect ruleId={} ruleName={} category={} pattern={} effectivePattern={} flags={} matched={} normalizedApplied={} textLength={} textPreview={}",
				rule.getId(), rule.getName(), rule.getCategory(), originalPattern, effectivePattern, config.getFlags(),
				findings.size(), normalizedApplied, text.length(), previewText(text));
		return findings;
	}

	private List<Finding> detectWithCompiledPattern(String text, CleaningRule rule, Pattern pattern,
			String replacement) {
		Matcher matcher = pattern.matcher(text);
		List<Finding> findings = new ArrayList<>();
		while (matcher.find()) {
			findings.add(Finding.builder()
				.type(rule.getCategory())
				.category(rule.getCategory())
				.severity(rule.getSeverity() != null ? rule.getSeverity() : 0.8)
				.start(matcher.start())
				.end(matcher.end())
				.detectorSource("L1_REGEX")
				.replacement(replacement)
				.build());
		}
		return findings;
	}

	private String resolveReplacement(RegexRuleConfig config) {
		if (config == null) {
			return DEFAULT_MASK;
		}
		if (log.isDebugEnabled()) {
			log.debug("Resolve replacement config: {}", JsonUtil.toJson(config));
		}
		if (RegexRuleConfig.MASK_MODE_DELETE.equalsIgnoreCase(config.getMaskMode())) {
			return "";
		}
		String maskText = config.getMaskText();
		if (maskText != null && !maskText.isBlank()) {
			return maskText;
		}
		return DEFAULT_MASK;
	}

	private String previewText(String text) {
		if (text == null) {
			return "";
		}
		String normalized = text.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
		if (normalized.length() <= 32) {
			return normalized;
		}
		return normalized.substring(0, 32) + "...";
	}

	private RegexRuleConfig parseConfig(String configJson, Long ruleId) {
		try {
			return JsonUtil.getObjectMapper().readValue(configJson, RegexRuleConfig.class);
		}
		catch (JsonProcessingException e) {
			log.warn("Failed to parse regex rule config for rule {}", ruleId, e);
			return null;
		}
	}

	private Pattern compilePattern(String pattern, String flagsText) {
		String key = pattern + "#" + flagsText;
		return PATTERN_CACHE.computeIfAbsent(key, k -> {
			try {
				int flags = parseFlags(flagsText);
				return Pattern.compile(pattern, flags);
			}
			catch (Exception e) {
				log.warn("Failed to compile regex pattern: {}", pattern, e);
				return null;
			}
		});
	}

	private String normalizeOverEscapedPattern(String pattern) {
		if (pattern == null || pattern.isBlank()) {
			return pattern;
		}
		return pattern.replace("\\\\d", "\\d")
			.replace("\\\\D", "\\D")
			.replace("\\\\w", "\\w")
			.replace("\\\\W", "\\W")
			.replace("\\\\s", "\\s")
			.replace("\\\\S", "\\S")
			.replace("\\\\b", "\\b")
			.replace("\\\\B", "\\B");
	}

	private int parseFlags(String flags) {
		if (flags == null || flags.isBlank()) {
			return 0;
		}
		int result = 0;
		String[] parts = flags.split(",");
		for (String part : parts) {
			String flag = part.trim().toUpperCase();
			switch (flag) {
				case "CASE_INSENSITIVE":
					result |= Pattern.CASE_INSENSITIVE;
					break;
				case "MULTILINE":
					result |= Pattern.MULTILINE;
					break;
				case "DOTALL":
					result |= Pattern.DOTALL;
					break;
				default:
					break;
			}
		}
		return result;
	}

}
