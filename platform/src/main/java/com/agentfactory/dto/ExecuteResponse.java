package com.agentfactory.dto;

import java.util.List;
import java.util.Map;

public record ExecuteResponse(
    String result,
    List<Map<String, Object>> steps,
    int totalTokens,
    String status
) {}
