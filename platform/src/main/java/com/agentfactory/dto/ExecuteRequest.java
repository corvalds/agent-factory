package com.agentfactory.dto;

public record ExecuteRequest(
    String background,
    String goal,
    String acceptanceCriteria,
    String agentType,
    String model,
    String apiKey
) {}
