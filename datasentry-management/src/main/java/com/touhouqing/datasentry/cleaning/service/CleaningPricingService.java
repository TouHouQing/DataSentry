package com.touhouqing.datasentry.cleaning.service;

import com.touhouqing.datasentry.entity.ModelConfig;
import com.touhouqing.datasentry.enums.ModelType;
import com.touhouqing.datasentry.mapper.ModelConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CleaningPricingService {

	public static final String DEFAULT_PROVIDER = "LOCAL_DEFAULT";

	public static final String DEFAULT_MODEL = "L3_LLM";

	private static final String DEFAULT_CURRENCY = "CNY";

	private static final long CACHE_TTL_MILLIS = 60000;

	private final ModelConfigMapper modelConfigMapper;

	private final Map<String, CacheEntry> pricingCache = new ConcurrentHashMap<>();

	public Pricing resolvePricing(String provider, String model) {
		String resolvedProvider = provider != null && !provider.isBlank() ? provider : DEFAULT_PROVIDER;
		String resolvedModel = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
		String cacheKey = resolvedProvider + "::" + resolvedModel;
		CacheEntry cached = pricingCache.get(cacheKey);
		if (cached != null && !cached.expired()) {
			return cached.pricing();
		}
		Pricing pricing = resolvePricingFromModelConfig(resolvedProvider, resolvedModel);
		if (pricing == null) {
			throw new PricingNotConfiguredException(
					String.format("模型 %s/%s 未配置价格，请先在模型配置中设置价格", resolvedProvider, resolvedModel));
		}
		pricingCache.put(cacheKey, new CacheEntry(pricing, Instant.now().toEpochMilli() + CACHE_TTL_MILLIS));
		return pricing;
	}

	private Pricing resolvePricingFromModelConfig(String provider, String model) {
		ModelConfig modelConfig = modelConfigMapper.findByProviderAndModelName(provider, model);
		if (modelConfig == null && DEFAULT_PROVIDER.equals(provider) && DEFAULT_MODEL.equals(model)) {
			modelConfig = modelConfigMapper.selectActiveByType(ModelType.CHAT.getCode());
		}
		if (modelConfig == null || modelConfig.getInputPricePer1k() == null
				|| modelConfig.getOutputPricePer1k() == null) {
			return null;
		}
		String currency = modelConfig.getCurrency() != null && !modelConfig.getCurrency().isBlank()
				? modelConfig.getCurrency() : DEFAULT_CURRENCY;
		String resolvedProvider = modelConfig.getProvider() != null && !modelConfig.getProvider().isBlank()
				? modelConfig.getProvider() : provider;
		String resolvedModel = modelConfig.getModelName() != null && !modelConfig.getModelName().isBlank()
				? modelConfig.getModelName() : model;
		return new Pricing(resolvedProvider, resolvedModel, safePrice(modelConfig.getInputPricePer1k()),
				safePrice(modelConfig.getOutputPricePer1k()), currency);
	}

	public List<PricingCatalogDTO> listActivePricingFromModelConfig() {
		List<ModelConfig> configs = modelConfigMapper.findAll();
		return configs.stream()
				.filter(config -> config.getInputPricePer1k() != null && config.getOutputPricePer1k() != null)
				.map(config -> {
					PricingCatalogDTO dto = new PricingCatalogDTO();
					dto.setProvider(config.getProvider());
					dto.setModel(config.getModelName());
					dto.setVersion("default");
					dto.setInputPricePer1k(config.getInputPricePer1k());
					dto.setOutputPricePer1k(config.getOutputPricePer1k());
					dto.setCurrency(config.getCurrency() != null ? config.getCurrency() : DEFAULT_CURRENCY);
					dto.setUpdatedTime(config.getPricingUpdatedAt() != null ? config.getPricingUpdatedAt()
							: config.getUpdatedTime());
					return dto;
				})
				.sorted((a, b) -> {
					if (a.getUpdatedTime() == null && b.getUpdatedTime() == null) {
						return 0;
					}
					if (a.getUpdatedTime() == null) {
						return 1;
					}
					if (b.getUpdatedTime() == null) {
						return -1;
					}
					return b.getUpdatedTime().compareTo(a.getUpdatedTime());
				})
				.collect(Collectors.toList());
	}

	public void clearCache() {
		pricingCache.clear();
	}

	private BigDecimal safePrice(BigDecimal value) {
		if (value == null || value.signum() < 0) {
			return BigDecimal.ZERO;
		}
		return value;
	}

	public record Pricing(String provider, String model, BigDecimal inputPricePer1k, BigDecimal outputPricePer1k,
			String currency) {
	}

	private record CacheEntry(Pricing pricing, long expiresAt) {

		boolean expired() {
			return Instant.now().toEpochMilli() > expiresAt;
		}

	}

	public static class PricingNotConfiguredException extends RuntimeException {

		public PricingNotConfiguredException(String message) {
			super(message);
		}

	}

	@lombok.Data
	public static class PricingCatalogDTO {

		private String provider;

		private String model;

		private String version;

		private BigDecimal inputPricePer1k;

		private BigDecimal outputPricePer1k;

		private String currency;

		private LocalDateTime updatedTime;

	}

}
