package com.touhouqing.datasentry.cleaning.detector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.util.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HeuristicL2DetectionProvider implements L2DetectionProvider {

	private static final String ANOMALY_ENTROPY = "ANOMALY_ENTROPY";

	private static final String ANOMALY_REPETITION = "ANOMALY_REPETITION";

	private static final String L2_REGEX = "L2_REGEX";

	private static final double DEFAULT_ENTROPY_THRESHOLD = 4.8;

	private static final int DEFAULT_REPETITION_THRESHOLD = 10;

	private final Map<Long, CachedRule> ruleCache = new ConcurrentHashMap<>();

	private record CachedRule(Pattern pattern, Double threshold, java.time.LocalDateTime version) {
	}

	@Override
	public String name() {
		return "DUMMY";
	}

	@Override
	public List<Finding> detect(String text, CleaningRule rule, CleaningPolicyConfig config) {
		if (text == null || text.isBlank()) {
			return List.of();
		}

		String category = rule.getCategory();

		if (L2_REGEX.equals(category)) {
			return detectRegex(text, rule);
		}

		// For other types, we still parse config per request for now (or could be
		// optimized similarly)
		Map<String, Object> ruleConfig = parseConfig(rule.getConfigJson());

		if (ANOMALY_ENTROPY.equals(category)) {
			return detectEntropy(text, ruleConfig, rule.getCategory());
		}
		else if (ANOMALY_REPETITION.equals(category)) {
			return detectRepetition(text, ruleConfig, rule.getCategory());
		}
		else {
			// 如果没有匹配到任何已知的 L2 策略，则默认不处理，彻底移除硬编码的 estimateRiskScore
			return List.of();
		}
	}

	private Map<String, Object> parseConfig(String json) {
		if (json == null || json.isBlank()) {
			return new HashMap<>();
		}
		try {
			ObjectMapper mapper = JsonUtil.getObjectMapper();
			return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
			});
		}
		catch (Exception e) {
			return new HashMap<>();
		}
	}

	private List<Finding> detectEntropy(String text, Map<String, Object> config, String category) {
		double threshold = DEFAULT_ENTROPY_THRESHOLD;
		if (config.containsKey("threshold")) {
			Object val = config.get("threshold");
			if (val instanceof Number) {
				threshold = ((Number) val).doubleValue();
			}
		}

		double entropy = calculateShannonEntropy(text);
		if (entropy > threshold) {
			return List.of(Finding.builder()
				.type(category)
				.category(category)
				.severity(0.8) // High probability of anomaly
				.start(0)
				.end(text.length())
				.detectorSource("L2_HEURISTIC_ENTROPY")
				.build());
		}
		return List.of();
	}

	private double calculateShannonEntropy(String text) {
		Map<Character, Integer> frequency = new HashMap<>();
		for (char c : text.toCharArray()) {
			frequency.put(c, frequency.getOrDefault(c, 0) + 1);
		}

		double entropy = 0.0;
		int len = text.length();
		for (Map.Entry<Character, Integer> entry : frequency.entrySet()) {
			double prob = (double) entry.getValue() / len;
			entropy -= prob * (Math.log(prob) / Math.log(2));
		}
		return entropy;
	}

	private List<Finding> detectRepetition(String text, Map<String, Object> config, String category) {
		int maxRepetition = DEFAULT_REPETITION_THRESHOLD;
		if (config.containsKey("maxRepetition")) {
			Object val = config.get("maxRepetition");
			if (val instanceof Number) {
				maxRepetition = ((Number) val).intValue();
			}
		}

		int currentMax = 0;
		int currentCount = 0;
		char lastChar = 0;
		int startPos = -1;
		int maxStartPos = -1;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == lastChar) {
				currentCount++;
			}
			else {
				if (currentCount > currentMax) {
					currentMax = currentCount;
					maxStartPos = i - currentCount;
				}
				currentCount = 1;
				lastChar = c;
			}
		}
		// Check last sequence
		if (currentCount > currentMax) {
			currentMax = currentCount;
			maxStartPos = text.length() - currentCount;
		}

		if (currentMax >= maxRepetition) {
			return List.of(Finding.builder()
				.type(category)
				.category(category)
				.severity(0.7)
				.start(maxStartPos)
				.end(maxStartPos + currentMax)
				.detectorSource("L2_HEURISTIC_REPETITION")
				.build());
		}
		return List.of();
	}

	private List<Finding> detectRegex(String text, CleaningRule rule) {
		Long ruleId = rule.getId();
		CachedRule cached = ruleId == null ? null : ruleCache.get(ruleId);
		if (cached == null || !Objects.equals(cached.version(), rule.getUpdatedTime())) {
			Map<String, Object> config = parseConfig(rule.getConfigJson());
			String patternStr = (String) config.get("pattern");
			if (patternStr == null || patternStr.isBlank()) {
				return List.of();
			}

			Pattern pattern;
			try {
				pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			}
			catch (Exception e) {
				return List.of();
			}

			double threshold = 0.9;
			if (config.containsKey("threshold")) {
				Object val = config.get("threshold");
				if (val instanceof Number) {
					threshold = ((Number) val).doubleValue();
				}
			}

			cached = new CachedRule(pattern, threshold, rule.getUpdatedTime());
			if (ruleId != null) {
				ruleCache.put(ruleId, cached);
			}
		}

		Matcher matcher = cached.pattern().matcher(text);
		List<Finding> findings = new ArrayList<>();
		while (matcher.find()) {
			findings.add(Finding.builder()
				.type(rule.getCategory())
				.category(rule.getCategory())
				.severity(cached.threshold())
				.start(matcher.start())
				.end(matcher.end())
				.detectorSource("L2_REGEX_MATCH")
				.build());
		}
		return findings;
	}

}
