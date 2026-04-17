package com.agentfactory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class AgentServiceClient {

    private final RestClient restClient;

    public AgentServiceClient(@Value("${af.agent-service-url:http://localhost:8000}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Map<String, Object> health() {
        return restClient.get()
                .uri("/health")
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> define(Map<String, Object> request) {
        return restClient.post()
                .uri("/define")
                .body(request)
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> execute(Map<String, Object> request) {
        return restClient.post()
                .uri("/execute")
                .body(request)
                .retrieve()
                .body(Map.class);
    }
}
