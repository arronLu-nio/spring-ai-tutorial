package com.example.springaitutorial.rag;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RAG 外部调用的统一保护层：超时、有限重试和降级。
 * 这里用 CompletableFuture 演示调用边界；生产环境应替换为统一线程池或 Resilience4j。
 */
@Component
public class RagResilienceService {

    private static final Logger log = LoggerFactory.getLogger(RagResilienceService.class);

    private final RagProperties properties;

    public RagResilienceService(RagProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(String operation, Supplier<T> action, Supplier<T> fallback) {
        int attempts = properties.getMaxRetries() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return CompletableFuture.supplyAsync(action)
                        .orTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                        .join();
            }
            catch (Exception ex) {
                log.warn("RAG 外部调用失败: operation={}, attempt={}/{}", operation, attempt, attempts);
                if (attempt < attempts) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        log.warn("RAG 外部调用降级: operation={}", operation);
        return fallback.get();
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(Duration.ofMillis(200L * attempt));
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
