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

@AllArgsConstructor
public class StreamLlmService implements LlmService {

	private final AiModelRegistry registry;
	private final AiCostTrackingService costTrackingService;

	@Override
	public Flux<ChatResponse> call(String system, String user) {
		return registry.getChatClient()
			.prompt()
			.system(system)
			.user(user)
			.stream()
			.chatResponse();
	}

	@Override
	public Flux<ChatResponse> callSystem(String system) {
		return registry.getChatClient()
			.prompt()
			.system(system)
			.stream()
			.chatResponse();
	}

	@Override
	public Flux<ChatResponse> callUser(String user) {
		return registry.getChatClient()
			.prompt()
			.user(user)
			.stream()
			.chatResponse();
	}

	private void trackCost(ChatResponse response) {
		if (costTrackingService != null) {
			try {
				costTrackingService.trackChatCost(response);
			} catch (Exception e) {
				// ignore tracking errors
			}
		}
	}

}
