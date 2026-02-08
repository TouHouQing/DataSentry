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

import com.touhouqing.datasentry.cleaning.advisor.CostTrackingAdvisor;
import com.touhouqing.datasentry.enums.ModelType;
import com.touhouqing.datasentry.dto.ModelConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelRegistry {

	private final DynamicModelFactory modelFactory;

	private final ModelConfigDataService modelConfigDataService;

	private final CostTrackingAdvisor costTrackingAdvisor;

	private final com.touhouqing.datasentry.cleaning.service.AiCostTrackingService costTrackingService;

	// 缓存对象 (volatile 保证可见性)
	private volatile ChatClient currentChatClient;

	private volatile EmbeddingModel currentEmbeddingModel;

	// =========================================================
	// 1. 获取 ChatClient (懒加载 + 缓存)
	// =========================================================
	public ChatClient getChatClient() {
		if (currentChatClient == null) {
			synchronized (this) {
				if (currentChatClient == null) {
					log.info("Initializing global ChatClient...");
					try {
						ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
						if (config != null) {
							ChatModel chatModel = modelFactory.createChatModel(config);
							// 核心：基于新 Model 创建新 Client，彻底消除旧参数缓存
							currentChatClient = ChatClient.builder(chatModel)
									.defaultAdvisors(costTrackingAdvisor)
									.build();
						}
					}
					catch (Exception e) {
						log.error("Failed to initialize ChatClient: {}", e.getMessage(), e);
					}

					// 兜底：如果还没初始化成功，抛出运行时异常，提示用户配置
					if (currentChatClient == null) {
						throw new RuntimeException(
								"No active CHAT model configured. Please configure it in the dashboard.");
					}
				}
			}
		}
		return currentChatClient;
	}

	// =========================================================
	// 2. 获取 EmbeddingModel (懒加载 + Dummy 兜底)
	// =========================================================
	public EmbeddingModel getEmbeddingModel() {
		if (currentEmbeddingModel == null) {
			synchronized (this) {
				if (currentEmbeddingModel == null) {
					log.info("Initializing global EmbeddingModel...");
					try {
						ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);
						if (config != null) {
							EmbeddingModel baseModel = modelFactory.createEmbeddingModel(config);
							// 核心：包装 EmbeddingModel 以实现成本追踪
							currentEmbeddingModel = new CostTrackingEmbeddingModel(baseModel, costTrackingService);
						}
					}
					catch (Exception e) {
						log.error("Failed to initialize EmbeddingModel: {}", e.getMessage());
					}

					// 兜底：为了防止 VectorStore Starter 启动时调用 dimensions() 报错
					// 我们必须返回一个"哑巴"模型，而不是 null 或 抛异常
					if (currentEmbeddingModel == null) {
						log.warn("Using DummyEmbeddingModel for fallback.");
						currentEmbeddingModel = new DummyEmbeddingModel();
					}
				}
			}
		}
		return currentEmbeddingModel;
	}

	// =========================================================
	// 3. 刷新/重置缓存 (用于热切换)
	// =========================================================

	public void refreshChat() {
		this.currentChatClient = null;
		log.info("Chat cache cleared.");
	}

	public void refreshEmbedding() {
		this.currentEmbeddingModel = null;
		log.info("Embedding cache cleared.");
	}

	// =========================================================
	// 4. 内部类：成本追踪包装器
	// =========================================================
	@RequiredArgsConstructor
	private static class CostTrackingEmbeddingModel implements EmbeddingModel {
		private final EmbeddingModel delegate;
		private final com.touhouqing.datasentry.cleaning.service.AiCostTrackingService costTrackingService;

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			EmbeddingResponse response = delegate.call(request);
			try {
				// 尝试获取 ThreadID（优先从 Aspect Context 获取，因为 GraphServiceImpl 设置了它）
				var context = com.touhouqing.datasentry.cleaning.context.AiCostContextHolder.getContext();
				String threadId = context != null ? context.threadId() : null;

				if (threadId == null) {
					var aspectContext = com.touhouqing.datasentry.cleaning.aspect.AiCostTrackingAspect.getContext();
					threadId = aspectContext != null ? aspectContext.threadId() : null;
				}

				if (threadId != null) {
					costTrackingService.trackEmbeddingCost(threadId, response);
				} else {
					// 如果没有 threadId，Service 内部会尝试用 threadAgentMap 兜底，但这里我们需要传一个 threadId 进去
					// 由于 interface 限制，我们无法直接传 null，但 Service 的方法签名需要 threadId。
					// 这里的矛盾在于：EmbeddingModel.call 接口本身没有 threadId 参数。
					// 实际上，Aspect 方式是更好的拦截点。
					// 但既然我们要用 Wrapper，我们就必须在这里尽力获取 threadId。
					// 如果这里拿不到 threadId，说明当前线程没有设置上下文。
					log.warn("❌ CostTrackingEmbeddingModel: No threadId found in context for embedding tracking.");
				}
			} catch (Exception e) {
				log.error("Failed to track embedding cost in wrapper", e);
			}
			return response;
		}

		@Override
		public float[] embed(Document document) {
			return this.embed(document.getText());
		}

		@Override
		public float[] embed(String text) {
			List<float[]> result = this.embed(List.of(text));
			return result.isEmpty() ? new float[0] : result.get(0);
		}

		@Override
		public List<float[]> embed(List<String> texts) {
			EmbeddingRequest request = new EmbeddingRequest(texts, OpenAiEmbeddingOptions.builder().build());
			EmbeddingResponse response = this.call(request);
			return response.getResults().stream().map(org.springframework.ai.embedding.Embedding::getOutput).toList();
		}

		@Override
		public EmbeddingResponse embedForResponse(List<String> texts) {
			EmbeddingRequest request = new EmbeddingRequest(texts, OpenAiEmbeddingOptions.builder().build());
			return this.call(request);
		}

		@Override
		public int dimensions() {
			return delegate.dimensions();
		}
	}

	// =========================================================
	// 5. 内部类：哑巴嵌入模型 (仅用于启动时防崩)
	// =========================================================
	private static class DummyEmbeddingModel implements EmbeddingModel {

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			throw new RuntimeException("No active EMBEDDING model. Please configure it first!");
		}

		@Override
		public float[] embed(Document document) {
			return new float[0];
		}

		@Override
		public float[] embed(String text) {
			return new float[0];
		}

		@Override
		public List<float[]> embed(List<String> texts) {
			return List.of();
		}

		@Override
		public EmbeddingResponse embedForResponse(List<String> texts) {
			return null;
		}

		// 关键：返回一个常用维度 (1536是OpenAI的维度)，骗过向量库的初始化检查
		@Override
		public int dimensions() {
			return 1536;
		}

	}

}
