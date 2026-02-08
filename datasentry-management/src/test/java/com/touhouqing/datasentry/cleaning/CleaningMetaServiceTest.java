package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.dto.CleaningOptionMetaView;
import com.touhouqing.datasentry.cleaning.dto.CleaningThresholdItemView;
import com.touhouqing.datasentry.cleaning.service.CleaningMetaService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CleaningMetaServiceTest {

	private final CleaningMetaService cleaningMetaService = new CleaningMetaService();

	@Test
	public void shouldProvideBasicOptionMeta() {
		CleaningOptionMetaView options = cleaningMetaService.getOptions();

		assertNotNull(options);
		assertTrue(options.getDefaultActions().stream().anyMatch(item -> "DETECT_ONLY".equals(item.getCode())));
		assertTrue(options.getRuleTypes().stream().anyMatch(item -> "REGEX".equals(item.getCode())));
		assertTrue(options.getReviewPolicies().stream().anyMatch(item -> "NEVER".equals(item.getCode())));
		assertNotNull(options.getJsonConfigTemplates().get("REGEX"));
		assertNotNull(options.getFieldHelp().get("policy.defaultAction"));
	}

	@Test
	public void shouldExposeDefaultThresholdValues() {
		CleaningOptionMetaView options = cleaningMetaService.getOptions();

		Optional<CleaningThresholdItemView> blockThreshold = options.getThresholdGuidance()
			.stream()
			.filter(item -> "blockThreshold".equals(item.getCode()))
			.findFirst();
		Optional<CleaningThresholdItemView> reviewThreshold = options.getThresholdGuidance()
			.stream()
			.filter(item -> "reviewThreshold".equals(item.getCode()))
			.findFirst();
		Optional<CleaningThresholdItemView> l2Threshold = options.getThresholdGuidance()
			.stream()
			.filter(item -> "l2Threshold".equals(item.getCode()))
			.findFirst();
		Optional<CleaningThresholdItemView> shadowRatio = options.getThresholdGuidance()
			.stream()
			.filter(item -> "shadowSampleRatio".equals(item.getCode()))
			.findFirst();

		assertTrue(blockThreshold.isPresent());
		assertTrue(reviewThreshold.isPresent());
		assertTrue(l2Threshold.isPresent());
		assertTrue(shadowRatio.isPresent());
		assertEquals(0.7, blockThreshold.get().getDefaultValue());
		assertEquals(0.4, reviewThreshold.get().getDefaultValue());
		assertEquals(0.6, l2Threshold.get().getDefaultValue());
		assertEquals(0.0, shadowRatio.get().getDefaultValue());
	}

	@Test
	public void shouldProvideGuidanceForBeginnerFriendlyUi() {
		CleaningOptionMetaView options = cleaningMetaService.getOptions();

		assertNotNull(options.getRuleTypeSchemas());
		assertNotNull(options.getRuleTypeSchemas().get("REGEX"));
		assertNotNull(options.getRuleTypeUiBehavior());
		assertTrue(options.getRuleTypeUiBehavior().containsKey("REGEX"));
		assertNotNull(options.getSeverityGuidance());
		assertFalse(options.getSeverityGuidance().isEmpty());
		assertNotNull(options.getRiskConfirmations());
		assertTrue(options.getRiskConfirmations().containsKey("WRITEBACK"));
		assertNotNull(options.getRegexTemplates());
		assertFalse(options.getRegexTemplates().isEmpty());
		assertEquals("PLACEHOLDER", options.getJsonConfigTemplates().get("REGEX") instanceof Map
				? ((Map<?, ?>) options.getJsonConfigTemplates().get("REGEX")).get("maskMode") : null);
		assertNotNull(options.getFieldHelp().get("policy.regex.maskMode"));
		assertNotNull(options.getFieldHelp().get("policy.regex.maskText"));
	}

}
