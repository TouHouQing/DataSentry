package com.touhouqing.datasentry.cleaning.detector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.service.CleaningOpsStateService;
import com.touhouqing.datasentry.cleaning.util.CleaningOutboundSanitizer;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudApiL2DetectionProvider implements L2DetectionProvider {

	private final DataSentryProperties dataSentryProperties;

	private final CleaningOpsStateService opsStateService;

	private final HttpClient cleaningCloudHttpClient;

	@Override
	public String name() {
		return "CLOUD_API";
	}

	@Override
	public boolean isReady() {
		DataSentryProperties.Cleaning.L2.CloudApi cloudApi = dataSentryProperties.getCleaning().getL2().getCloudApi();
		return cloudApi != null && cloudApi.getUrl() != null && !cloudApi.getUrl().isBlank();
	}

	@Override
	public List<Finding> detect(String text, CleaningRule rule, CleaningPolicyConfig config) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		if (!isReady()) {
			throw new IllegalStateException("Cloud API URL is not configured");
		}
		DataSentryProperties.Cleaning.L2.CloudApi cloudApi = dataSentryProperties.getCleaning().getL2().getCloudApi();
		double providerThreshold = dataSentryProperties.getCleaning().getL2().getThreshold();
		double policyThreshold = config != null ? config.resolvedL2Threshold() : 0.6;
		double threshold = Math.max(0.0, Math.min(1.0, Math.max(providerThreshold, policyThreshold)));
		long startNanos = System.nanoTime();
		try {
			String outboundText = resolveOutboundText(text, config);
			CloudApiResult result = callCloudApi(outboundText, rule, threshold, cloudApi);
			opsStateService.markCloudInferenceSuccess(elapsedMillis(startNanos));
			if (result.score() < threshold) {
				return List.of();
			}
			List<Finding> findings = new ArrayList<>();
			findings.add(Finding.builder()
				.type(rule.getCategory())
				.category(rule.getCategory())
				.severity(result.score())
				.start(0)
				.end(text.length())
				.detectorSource("L2_CLOUD_API")
				.build());
			return findings;
		}
		catch (Exception e) {
			opsStateService.markCloudInferenceFailure(elapsedMillis(startNanos));
			throw new IllegalStateException("Cloud API inference failed", e);
		}
	}

	private CloudApiResult callCloudApi(String text, CleaningRule rule, double threshold,
			DataSentryProperties.Cleaning.L2.CloudApi cloudApi) throws Exception {
		ObjectNode payload = JsonUtil.getObjectMapper().createObjectNode();
		payload.put("text", text);
		if (rule != null && rule.getCategory() != null) {
			payload.put("category", rule.getCategory());
		}
		payload.put("threshold", threshold);
		if (cloudApi.getModel() != null && !cloudApi.getModel().isBlank()) {
			payload.put("model", cloudApi.getModel());
		}
		String requestBody = JsonUtil.getObjectMapper().writeValueAsString(payload);

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
			.uri(URI.create(cloudApi.getUrl()))
			.timeout(Duration.ofMillis(Math.max(cloudApi.getTimeoutMs(), 100)))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(requestBody));

		if (cloudApi.getApiKey() != null && !cloudApi.getApiKey().isBlank()) {
			String headerName = cloudApi.getAuthHeader() != null && !cloudApi.getAuthHeader().isBlank()
					? cloudApi.getAuthHeader() : "Authorization";
			String headerValue = cloudApi.getApiKey();
			if (cloudApi.getAuthPrefix() != null && !cloudApi.getAuthPrefix().isBlank()) {
				headerValue = cloudApi.getAuthPrefix() + " " + cloudApi.getApiKey();
			}
			requestBuilder.header(headerName, headerValue);
		}

		HttpResponse<String> response = cleaningCloudHttpClient.send(requestBuilder.build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("Cloud API returned status=" + response.statusCode());
		}
		return parseResponse(response.body());
	}

	private CloudApiResult parseResponse(String responseBody) throws Exception {
		JsonNode root = JsonUtil.getObjectMapper().readTree(responseBody);
		Double score = firstNumber(root.path("score"), root.path("riskScore"), root.path("risk_score"),
				root.path("data").path("score"), root.path("data").path("riskScore"),
				root.path("data").path("risk_score"), root.path("result").path("score"),
				root.path("result").path("riskScore"), firstPredictionScore(root));
		String label = firstText(root.path("label"), root.path("decision"), root.path("result").path("label"),
				root.path("data").path("label"));

		if (score == null && label != null) {
			String normalized = label.trim().toUpperCase(Locale.ROOT);
			if ("RISK".equals(normalized) || "SUSPICIOUS".equals(normalized) || "BLOCK".equals(normalized)) {
				score = 1.0;
			}
			if ("SAFE".equals(normalized) || "PASS".equals(normalized) || "ALLOW".equals(normalized)) {
				score = 0.0;
			}
		}
		if (score == null) {
			throw new IllegalStateException("Cloud API response missing score field");
		}
		if (Double.isNaN(score) || Double.isInfinite(score)) {
			throw new IllegalStateException("Cloud API score is invalid");
		}
		if (score < 0.0 || score > 1.0) {
			score = 1.0 / (1.0 + Math.exp(-score));
		}
		return new CloudApiResult(Math.max(0.0, Math.min(1.0, score)), label);
	}

	private JsonNode firstPredictionScore(JsonNode root) {
		JsonNode predictions = root.path("predictions");
		if (predictions.isArray() && !predictions.isEmpty()) {
			return predictions.get(0).path("score");
		}
		return null;
	}

	private Double firstNumber(JsonNode... nodes) {
		if (nodes == null) {
			return null;
		}
		for (JsonNode node : nodes) {
			if (node == null || node.isMissingNode() || node.isNull()) {
				continue;
			}
			if (node.isNumber()) {
				return node.asDouble();
			}
			if (node.isTextual()) {
				try {
					return Double.parseDouble(node.asText().trim());
				}
				catch (NumberFormatException ignored) {
				}
			}
		}
		return null;
	}

	private String firstText(JsonNode... nodes) {
		if (nodes == null) {
			return null;
		}
		for (JsonNode node : nodes) {
			if (node == null || node.isMissingNode() || node.isNull()) {
				continue;
			}
			String value = node.asText();
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private long elapsedMillis(long startNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
	}

	private String resolveOutboundText(String text, CleaningPolicyConfig config) {
		if (config == null || !config.resolvedOutboundSanitizeEnabled()) {
			return text;
		}
		return CleaningOutboundSanitizer.sanitize(text, config.resolvedOutboundSanitizeMode());
	}

	private record CloudApiResult(double score, String label) {
	}

}
