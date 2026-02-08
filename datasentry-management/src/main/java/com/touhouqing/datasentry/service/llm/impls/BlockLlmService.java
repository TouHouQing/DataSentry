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
package com.touhouqing.datasentry.service.llm.impls;

import com.touhouqing.datasentry.cleaning.service.AiCostTrackingService;
import com.touhouqing.datasentry.service.aimodelconfig.AiModelRegistry;
import com.touhouqing.datasentry.service.llm.LlmService;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class BlockLlmService implements LlmService {

	private final AiModelRegistry registry;

	private final AiCostTrackingService costTrackingService;

	@Override
	public Flux<ChatResponse> call(String system, String user) {
		return Mono.fromCallable(() -> {
			ChatResponse response = registry.getChatClient().prompt().system(system).user(user).call().chatResponse();
			return response;
		}).flux();
	}

	@Override
	public Flux<ChatResponse> callSystem(String system) {
		return Mono.fromCallable(() -> {
			ChatResponse response = registry.getChatClient().prompt().system(system).call().chatResponse();
			return response;
		}).flux();
	}

	@Override
	public Flux<ChatResponse> callUser(String user) {
		return Mono.fromCallable(() -> {
			ChatResponse response = registry.getChatClient().prompt().user(user).call().chatResponse();
			return response;
		}).flux();
	}

	private void trackCost(ChatResponse response) {
		if (costTrackingService != null) {
			// We can get threadId from the aspect's context holder since we set it in
			// GraphServiceImpl
			// Or we can rely on AiCostTrackingService to find the context if we refactor
			// it to use the static holder
			// For now, let's use a simpler approach: get the threadId from the Aspect's
			// context
			try {
				// We need the threadId to track cost. Since GraphServiceImpl sets it in
				// AiCostTrackingAspect's ThreadLocal,
				// we can access it if we expose a getter, or we can just use the
				// ThreadLocal in AiCostTrackingService directly.
				// However, AiCostTrackingService currently relies on a map passed by
				// threadId.
				// Let's modify AiCostTrackingService to use the ThreadLocal context too.
				costTrackingService.trackChatCost(response);
			}
			catch (Exception e) {
				// ignore tracking errors to not disrupt flow
			}
		}
	}

}
