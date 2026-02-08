package com.touhouqing.datasentry.cleaning.context;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AiCostContextHolder {

    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT = new InheritableThreadLocal<>();

    public static void setContext(String threadId, Long agentId) {
        REQUEST_CONTEXT.set(new RequestContext(threadId, agentId));
    }

    public static void clearContext() {
        REQUEST_CONTEXT.remove();
    }

    public static RequestContext getContext() {
        return REQUEST_CONTEXT.get();
    }

    public record RequestContext(String threadId, Long agentId) {}
}
