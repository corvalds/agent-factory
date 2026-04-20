package com.agentfactory.service;

import com.agentfactory.dto.DefineRequest;
import com.agentfactory.dto.DefineResponse;
import com.agentfactory.dto.ExecuteRequest;
import com.agentfactory.dto.ExecuteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class AgentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceClient.class);
    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {1000, 2000, 4000};

    private final RestClient restClient;

    public AgentServiceClient(
            @Value("${af.agent-service-url:http://localhost:8000}") String baseUrl,
            @Value("${af.internal-key:internal-dev-key}") String internalKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Key", internalKey)
                .build();
    }

    public Map<String, Object> health() {
        return restClient.get()
                .uri("/health")
                .retrieve()
                .body(Map.class);
    }

    public DefineResponse define(DefineRequest request) {
        return withRetry("define", () ->
            restClient.post()
                .uri("/define")
                .body(request)
                .retrieve()
                .body(DefineResponse.class)
        );
    }

    public ExecuteResponse execute(ExecuteRequest request) {
        return withRetry("execute", () ->
            restClient.post()
                .uri("/execute")
                .body(request)
                .retrieve()
                .body(ExecuteResponse.class)
        );
    }

    private <T> T withRetry(String operation, java.util.function.Supplier<T> action) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                if (attempt == MAX_RETRIES - 1) {
                    log.error("Agent service {} failed after {} retries: {}", operation, MAX_RETRIES, e.getMessage());
                    throw new RuntimeException("Agent service unavailable after " + MAX_RETRIES + " retries", e);
                }
                log.warn("Agent service {} attempt {} failed, retrying in {}ms: {}", operation, attempt + 1, BACKOFF_MS[attempt], e.getMessage());
                try {
                    Thread.sleep(BACKOFF_MS[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }
}
