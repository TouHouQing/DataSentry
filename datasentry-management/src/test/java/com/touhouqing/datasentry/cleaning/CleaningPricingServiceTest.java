package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.service.CleaningPricingService;
import com.touhouqing.datasentry.entity.ModelConfig;
import com.touhouqing.datasentry.mapper.ModelConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CleaningPricingServiceTest {

	@Mock
	private ModelConfigMapper modelConfigMapper;

	@Test
	public void shouldThrowExceptionWhenConfigMissing() {
		when(modelConfigMapper.findByProviderAndModelName("LOCAL_DEFAULT", "L3_LLM")).thenReturn(null);
		// Default fallback logic also returns null for active CHAT model in this test scenario
		when(modelConfigMapper.selectActiveByType("CHAT")).thenReturn(null);

		CleaningPricingService service = new CleaningPricingService(modelConfigMapper);

		assertThrows(CleaningPricingService.PricingNotConfiguredException.class, () -> {
			service.resolvePricing(null, null);
		});
	}

	@Test
	public void shouldThrowExceptionWhenPriceMissingInConfig() {
		ModelConfig modelConfig = new ModelConfig();
		modelConfig.setProvider("P");
		modelConfig.setModelName("M");
		// Price is null
		when(modelConfigMapper.findByProviderAndModelName("P", "M")).thenReturn(modelConfig);

		CleaningPricingService service = new CleaningPricingService(modelConfigMapper);

		assertThrows(CleaningPricingService.PricingNotConfiguredException.class, () -> {
			service.resolvePricing("P", "M");
		});
	}

	@Test
	public void shouldUseModelConfigPricing() {
		ModelConfig modelConfig = new ModelConfig();
		modelConfig.setProvider("P");
		modelConfig.setModelName("M");
		modelConfig.setInputPricePer1k(new BigDecimal("0.200000"));
		modelConfig.setOutputPricePer1k(new BigDecimal("0.300000"));
		modelConfig.setCurrency("USD");
		when(modelConfigMapper.findByProviderAndModelName("P", "M")).thenReturn(modelConfig);

		CleaningPricingService service = new CleaningPricingService(modelConfigMapper);

		CleaningPricingService.Pricing pricing = service.resolvePricing("P", "M");

		assertEquals(new BigDecimal("0.200000"), pricing.inputPricePer1k());
		assertEquals(new BigDecimal("0.300000"), pricing.outputPricePer1k());
		assertEquals("USD", pricing.currency());
	}

	@Test
	public void shouldUseActiveChatModelPricingWhenDefaultKeyRequested() {
		ModelConfig activeChatConfig = new ModelConfig();
		activeChatConfig.setProvider("deepseek");
		activeChatConfig.setModelName("deepseek-chat");
		activeChatConfig.setInputPricePer1k(new BigDecimal("0.111111"));
		activeChatConfig.setOutputPricePer1k(new BigDecimal("0.222222"));
		activeChatConfig.setCurrency("CNY");

		// When requesting default provider/model
		when(modelConfigMapper.findByProviderAndModelName("LOCAL_DEFAULT", "L3_LLM")).thenReturn(null);
		when(modelConfigMapper.selectActiveByType("CHAT")).thenReturn(activeChatConfig);

		CleaningPricingService service = new CleaningPricingService(modelConfigMapper);

		CleaningPricingService.Pricing pricing = service.resolvePricing(CleaningPricingService.DEFAULT_PROVIDER,
				CleaningPricingService.DEFAULT_MODEL);

		assertEquals("deepseek", pricing.provider());
		assertEquals("deepseek-chat", pricing.model());
		assertEquals(new BigDecimal("0.111111"), pricing.inputPricePer1k());
		assertEquals(new BigDecimal("0.222222"), pricing.outputPricePer1k());
	}

}
