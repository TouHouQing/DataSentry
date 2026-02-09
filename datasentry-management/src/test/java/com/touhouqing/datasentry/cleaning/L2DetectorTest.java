package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.detector.CloudApiL2DetectionProvider;
import com.touhouqing.datasentry.cleaning.detector.HeuristicL2DetectionProvider;
import com.touhouqing.datasentry.cleaning.detector.L2DetectionProviderRouter;
import com.touhouqing.datasentry.cleaning.detector.L2Detector;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class L2DetectorTest {

	private final DataSentryProperties properties = new DataSentryProperties();

	private final CleaningOpsStateService opsStateService = new CleaningOpsStateService();

	private final HeuristicL2DetectionProvider heuristicProvider = new HeuristicL2DetectionProvider();

	private final OnnxL2DetectionProvider onnxProvider = new OnnxL2DetectionProvider(properties, opsStateService);

	private final CloudApiL2DetectionProvider cloudApiProvider = new CloudApiL2DetectionProvider(properties,
			opsStateService, HttpClient.newHttpClient());

	private final L2DetectionProviderRouter providerRouter = new L2DetectionProviderRouter(heuristicProvider,
			onnxProvider, cloudApiProvider, properties, opsStateService);

	private final L2Detector detector = new L2Detector(providerRouter);

	@Test
	public void shouldReturnFindingForHeuristicRule() {
		properties.getCleaning().getL2().setProvider("DUMMY");
		CleaningPolicyConfig config = CleaningPolicyConfig.builder().build();
		CleaningRule rule = CleaningRule.builder()
			.category("ANOMALY_REPETITION")
			.configJson("{\"maxRepetition\": 5}")
			.build();

		List<Finding> findings = detector.detect("正常内容 aaaaaa", rule, config);

		assertEquals(1, findings.size());
		assertEquals("L2_HEURISTIC_REPETITION", findings.get(0).getDetectorSource());
	}

	@Test
	public void shouldReturnEmptyWhenHeuristicRuleDoesNotMatch() {
		properties.getCleaning().getL2().setProvider("DUMMY");
		CleaningPolicyConfig config = CleaningPolicyConfig.builder().build();
		CleaningRule rule = CleaningRule.builder()
			.category("ANOMALY_REPETITION")
			.configJson("{\"maxRepetition\": 8}")
			.build();

		List<Finding> findings = detector.detect("正常内容", rule, config);

		assertTrue(findings.isEmpty());
	}

}
