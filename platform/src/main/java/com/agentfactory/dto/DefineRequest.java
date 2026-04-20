package com.agentfactory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record DefineRequest(
    String message,
    List<Map<String, String>> conversation,
    String model,
    @JsonProperty("api_key") String apiKey,
    @JsonProperty("base_url") String baseUrl
) {}
