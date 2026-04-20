package com.agentfactory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecuteRequest(
    String background,
    String goal,
    @JsonProperty("acceptance_criteria") String acceptanceCriteria,
    @JsonProperty("agent_type") String agentType,
    String model,
    @JsonProperty("api_key") String apiKey,
    @JsonProperty("base_url") String baseUrl
) {}
