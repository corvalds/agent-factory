package com.agentfactory.controller;

import com.agentfactory.dto.DefineResponse;
import com.agentfactory.model.ConversationSession;
import com.agentfactory.model.Task;
import com.agentfactory.service.TaskDefinitionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks/define")
public class TaskDefinitionController {

    private final TaskDefinitionService definitionService;

    public TaskDefinitionController(TaskDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> start() {
        ConversationSession session = definitionService.startConversation();
        return Map.of(
            "sessionId", session.getId(),
            "expiresAt", session.getExpiresAt().toString(),
            "message", "Describe your task in plain language. I'll help you define it clearly."
        );
    }

    @PostMapping("/{sessionId}")
    public DefineResponse message(@PathVariable String sessionId, @RequestBody MessageRequest request) {
        return definitionService.processMessage(sessionId, request.message(), request.model());
    }

    @PostMapping("/{sessionId}/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public Task confirm(@PathVariable String sessionId, @RequestBody ConfirmRequest request) {
        return definitionService.confirmDefinition(
            sessionId,
            request.agentType(),
            request.modelId(),
            request.sandboxEnabled() != null ? request.sandboxEnabled() : false
        );
    }

    record MessageRequest(String message, String model) {}
    record ConfirmRequest(String agentType, String modelId, Boolean sandboxEnabled) {}
}
