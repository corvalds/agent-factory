package com.agentfactory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record ExecuteResponse(
    String result,
    List<Map<String, Object>> steps,
    @JsonProperty("total_tokens") int totalTokens,
    String status,
    @JsonProperty("mr_url") String mrUrl
) {}
