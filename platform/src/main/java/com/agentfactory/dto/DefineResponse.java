package com.agentfactory.dto;

import java.util.Map;

public record DefineResponse(
    String reply,
    Map<String, String> structured,
    boolean isComplete
) {}
