package com.touhouqing.datasentry.cleaning.advisor;

import com.touhouqing.datasentry.cleaning.context.AiCostContextHolder;
import com.touhouqing.datasentry.cleaning.service.AiCostTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class CostTrackingAdvisor implements CallAdvisor, StreamAdvisor {

	private final AiCostTrackingService costTrackingService;

	@Override
	public String getName() {
		return "CostTrackingAdvisor";
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		ChatClientResponse response = chain.nextCall(request);

		try {
			AiCostContextHolder.RequestContext context = AiCostContextHolder.getContext();
			if (context != null) {
				costTrackingService.trackChatCost(context.threadId(), response.chatResponse());
			}
			else {
				costTrackingService.trackChatCost(response.chatResponse());
			}
		}
		catch (Exception e) {
			log.error("Failed to track cost in advisor", e);
		}

		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		AiCostContextHolder.RequestContext context = AiCostContextHolder.getContext();
		String capturedThreadId = context != null ? context.threadId() : null;
		if (capturedThreadId == null) {
			log.debug("CostTrackingAdvisor stream: no context at assembly. thread={}",
					Thread.currentThread().getName());
		}
		AtomicBoolean fallbackLogged = new AtomicBoolean(false);
		AtomicBoolean tracked = new AtomicBoolean(false);
		return chain.nextStream(request).doOnNext(response -> {
			try {
				if (tracked.get() || !hasUsage(response)) {
					return;
				}
				if (capturedThreadId != null) {
					costTrackingService.trackChatCost(capturedThreadId, response.chatResponse());
					tracked.set(true);
					return;
				}
				AiCostContextHolder.RequestContext runtimeContext = AiCostContextHolder.getContext();
				if (runtimeContext != null && runtimeContext.threadId() != null) {
					costTrackingService.trackChatCost(runtimeContext.threadId(), response.chatResponse());
					tracked.set(true);
				}
				else {
					if (fallbackLogged.compareAndSet(false, true)) {
						log.debug("CostTrackingAdvisor stream: fallback tracking without threadId. thread={}",
								Thread.currentThread().getName());
					}
					costTrackingService.trackChatCost(response.chatResponse());
					tracked.set(true);
				}
			}
			catch (Exception e) {
				log.error("Failed to track cost in advisor (stream)", e);
			}
		});
	}

	private boolean hasUsage(ChatClientResponse response) {
		if (response == null || response.chatResponse() == null || response.chatResponse().getMetadata() == null
				|| response.chatResponse().getMetadata().getUsage() == null) {
			return false;
		}
		Integer promptTokens = response.chatResponse().getMetadata().getUsage().getPromptTokens();
		Integer completionTokens = response.chatResponse().getMetadata().getUsage().getCompletionTokens();
		return (promptTokens != null && promptTokens > 0) || (completionTokens != null && completionTokens > 0);
	}

}
