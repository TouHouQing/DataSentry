package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.detector.CloudApiL2DetectionProvider;
import com.touhouqing.datasentry.cleaning.detector.HeuristicL2DetectionProvider;
import com.touhouqing.datasentry.cleaning.detector.L2DetectionProviderRouter;
import com.touhouqing.datasentry.cleaning.detector.OnnxL2DetectionProvider;
import com.touhouqing.datasentry.cleaning.model.CleaningPolicyConfig;
import com.touhouqing.datasentry.cleaning.model.CleaningRule;
import com.touhouqing.datasentry.cleaning.model.Finding;
import com.touhouqing.datasentry.cleaning.service.CleaningOpsStateService;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class L2DetectionProviderRouterTest {

	@Test
	public void shouldUseDummyProviderByDefault() {
		DataSentryProperties properties = new DataSentryProperties();
		properties.getCleaning().getL2().setProvider("DUMMY");
		CleaningOpsStateService opsStateService = new CleaningOpsStateService();
		L2DetectionProviderRouter router = new L2DetectionProviderRouter(new HeuristicL2DetectionProvider(),
				new OnnxL2DetectionProvider(properties, opsStateService),
				new CloudApiL2DetectionProvider(properties, opsStateService, HttpClient.newHttpClient()), properties,
				opsStateService);
		CleaningRule rule = CleaningRule.builder()
			.category("L2_REGEX")
			.configJson("{\"pattern\":\"兼职转账\",\"threshold\":0.9}")
			.build();
		List<Finding> findings = router.detect("点击链接领取兼职转账福利", rule,
				CleaningPolicyConfig.builder().l2Threshold(0.5).build());
		assertEquals(1, findings.size());
		assertEquals("DUMMY/OK", opsStateService.getL2ProviderStatus());
	}

	@Test
	public void shouldFallbackToDummyWhenOnnxModelUnavailable() {
		DataSentryProperties properties = new DataSentryProperties();
		properties.getCleaning().getL2().setProvider("ONNX");
		properties.getCleaning().getL2().setOnnxModelPath("/tmp/not-exists.onnx");
		CleaningOpsStateService opsStateService = new CleaningOpsStateService();
		L2DetectionProviderRouter router = new L2DetectionProviderRouter(new HeuristicL2DetectionProvider(),
				new OnnxL2DetectionProvider(properties, opsStateService),
				new CloudApiL2DetectionProvider(properties, opsStateService, HttpClient.newHttpClient()), properties,
				opsStateService);
		CleaningRule rule = CleaningRule.builder()
			.category("L2_REGEX")
			.configJson("{\"pattern\":\"兼职转账\",\"threshold\":0.9}")
			.build();
		List<Finding> findings = router.detect("点击链接领取兼职转账福利", rule,
				CleaningPolicyConfig.builder().l2Threshold(0.5).build());
		assertEquals(1, findings.size());
		assertEquals("ONNX/DEGRADED", opsStateService.getL2ProviderStatus());
		assertEquals(1L, opsStateService.getOnnxFallbackCount());
	}

	@Test
	public void shouldFallbackToDummyWhenCloudApiNotConfigured() {
		DataSentryProperties properties = new DataSentryProperties();
		properties.getCleaning().getL2().setProvider("CLOUD_API");
		CleaningOpsStateService opsStateService = new CleaningOpsStateService();
		L2DetectionProviderRouter router = new L2DetectionProviderRouter(new HeuristicL2DetectionProvider(),
				new OnnxL2DetectionProvider(properties, opsStateService),
				new CloudApiL2DetectionProvider(properties, opsStateService, HttpClient.newHttpClient()), properties,
				opsStateService);
		CleaningRule rule = CleaningRule.builder()
			.category("L2_REGEX")
			.configJson("{\"pattern\":\"兼职转账\",\"threshold\":0.9}")
			.build();
		List<Finding> findings = router.detect("点击链接领取兼职转账福利", rule,
				CleaningPolicyConfig.builder().l2Threshold(0.5).build());
		assertEquals(1, findings.size());
		assertEquals("CLOUD_API/DEGRADED", opsStateService.getL2ProviderStatus());
		assertEquals(1L, opsStateService.getCloudFallbackCount());
	}

	@Test
	public void shouldLoadOnnxRuntimeJni() {
		DataSentryProperties properties = new DataSentryProperties();
		CleaningOpsStateService opsStateService = new CleaningOpsStateService();
		OnnxL2DetectionProvider provider = new OnnxL2DetectionProvider(properties, opsStateService);
		String runtimeVersion = provider.runtimeVersion();
		assertFalse(runtimeVersion == null || runtimeVersion.isBlank());
	}

}
