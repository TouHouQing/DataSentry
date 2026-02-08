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
package com.touhouqing.datasentry.service.llm;

import com.touhouqing.datasentry.cleaning.service.AiCostTrackingService;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import com.touhouqing.datasentry.service.aimodelconfig.AiModelRegistry;
import com.touhouqing.datasentry.service.llm.impls.BlockLlmService;
import com.touhouqing.datasentry.service.llm.impls.StreamLlmService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class LlmServiceFactory implements FactoryBean<LlmService> {

	private final DataSentryProperties properties;

	private final AiModelRegistry aiModelRegistry;

	private final AiCostTrackingService aiCostTrackingService;

	@Override
	public LlmService getObject() {
		if (LlmServiceEnum.BLOCK.equals(properties.getLlmServiceType())) {
			return new BlockLlmService(aiModelRegistry, aiCostTrackingService);
		}
		else {
			return new StreamLlmService(aiModelRegistry, aiCostTrackingService);
		}
	}

	@Override
	public Class<?> getObjectType() {
		return LlmService.class;
	}

}
