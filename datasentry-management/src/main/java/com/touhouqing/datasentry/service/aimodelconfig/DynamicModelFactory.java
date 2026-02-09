/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.touhouqing.datasentry.service.aimodelconfig;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.touhouqing.datasentry.dto.ModelConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class DynamicModelFactory {

	/**
	 * 根据 provider 动态创建 ChatModel。
	 */
	public ChatModel createChatModel(ModelConfigDTO config) {

		log.info("Creating NEW ChatModel instance. Provider: {}, Model: {}, BaseUrl: {}", config.getProvider(),
				config.getModelName(), config.getBaseUrl());
		checkBasic(config);

		if (isDashScopeProvider(config.getProvider())) {
			String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
			DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(apiKey).baseUrl(config.getBaseUrl());
			if (StringUtils.hasText(config.getCompletionsPath())) {
				apiBuilder.completionsPath(config.getCompletionsPath());
			}
			DashScopeApi dashScopeApi = apiBuilder.build();
			DashScopeChatOptions dashScopeChatOptions = DashScopeChatOptions.builder()
				.model(config.getModelName())
				.temperature(config.getTemperature())
				.maxToken(config.getMaxTokens())
				.stream(true)
				.build();
			return DashScopeChatModel.builder().dashScopeApi(dashScopeApi).defaultOptions(dashScopeChatOptions).build();
		}

		String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
		OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(apiKey).baseUrl(config.getBaseUrl());

		if (StringUtils.hasText(config.getCompletionsPath())) {
			apiBuilder.completionsPath(config.getCompletionsPath());
		}
		OpenAiApi openAiApi = apiBuilder.build();

		OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
			.model(config.getModelName())
			.temperature(config.getTemperature())
			.maxTokens(config.getMaxTokens())
			.streamUsage(true)
			.build();
		return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(openAiChatOptions).build();
	}

	private static void checkBasic(ModelConfigDTO config) {
		Assert.hasText(config.getBaseUrl(), "baseUrl must not be empty");
		if (!"custom".equalsIgnoreCase(config.getProvider())) {
			Assert.hasText(config.getApiKey(), "apiKey must not be empty");
		}
		Assert.hasText(config.getModelName(), "modelName must not be empty");
	}

	private static boolean isDashScopeProvider(String provider) {
		if (!StringUtils.hasText(provider)) {
			return false;
		}
		return "dashscope".equalsIgnoreCase(provider) || "aliyun".equalsIgnoreCase(provider)
				|| "tongyi".equalsIgnoreCase(provider) || "qwen".equalsIgnoreCase(provider);
	}

	/**
	 * Embedding 同理
	 */
	public EmbeddingModel createEmbeddingModel(ModelConfigDTO config) {
		log.info("Creating NEW EmbeddingModel instance. Provider: {}, Model: {}, BaseUrl: {}", config.getProvider(),
				config.getModelName(), config.getBaseUrl());
		checkBasic(config);

		String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
		OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(apiKey).baseUrl(config.getBaseUrl());

		if (StringUtils.hasText(config.getEmbeddingsPath())) {
			apiBuilder.embeddingsPath(config.getEmbeddingsPath());
		}

		OpenAiApi openAiApi = apiBuilder.build();
		return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
				OpenAiEmbeddingOptions.builder().model(config.getModelName()).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

}
