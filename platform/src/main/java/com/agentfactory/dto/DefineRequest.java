package com.agentfactory.dto;

import java.util.List;
import java.util.Map;

public record DefineRequest(
    String message,
    List<Map<String, String>> conversation,
    String model
) {}
