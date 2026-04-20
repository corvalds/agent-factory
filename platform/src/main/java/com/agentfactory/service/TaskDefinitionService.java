package com.agentfactory.service;

import com.agentfactory.dto.DefineRequest;
import com.agentfactory.dto.DefineResponse;
import com.agentfactory.model.ConversationSession;
import com.agentfactory.model.Task;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TaskDefinitionService {

    private final ConversationSessionService sessionService;
    private final AgentServiceClient agentClient;
    private final TaskService taskService;

    public TaskDefinitionService(ConversationSessionService sessionService,
                                  AgentServiceClient agentClient,
                                  TaskService taskService) {
        this.sessionService = sessionService;
        this.agentClient = agentClient;
        this.taskService = taskService;
    }

    public ConversationSession startConversation() {
        return sessionService.create();
    }

    public DefineResponse processMessage(String sessionId, String message, String model) {
        ConversationSession session = sessionService.get(sessionId);

        if (!"active".equals(session.getStatus())) {
            throw new IllegalStateException("Session is not active: " + session.getStatus());
        }

        session.addMessage("user", message);
        session.incrementTurnCount();

        boolean forceComplete = session.isAtTurnLimit();

        DefineRequest request = new DefineRequest(
            forceComplete ? message + "\n\n[SYSTEM: This is the final turn. Output the structured task definition now as JSON with keys: background, goal, acceptance_criteria.]" : message,
            session.getMessages(),
            model != null ? model : "gpt-4o"
        );

        DefineResponse response = agentClient.define(request);

        session.addMessage("assistant", response.reply());

        if (response.structured() != null) {
            session.setStructuredDefinition(response.structured());
        }

        return new DefineResponse(
            response.reply(),
            response.structured(),
            response.isComplete() || forceComplete
        );
    }

    public Task confirmDefinition(String sessionId, String agentType, String modelId, boolean sandboxEnabled) {
        ConversationSession session = sessionService.get(sessionId);
        Map<String, String> definition = session.getStructuredDefinition();

        if (definition == null) {
            throw new IllegalStateException("No structured definition available. Continue the conversation first.");
        }

        Task task = new Task();
        task.setName(definition.getOrDefault("goal", "Untitled task"));
        task.setBackground(definition.get("background"));
        task.setGoal(definition.get("goal"));
        task.setAcceptanceCriteria(definition.get("acceptance_criteria"));
        task.setAgentType(agentType != null ? agentType : "general-purpose");
        task.setModelId(modelId);
        task.setSandboxEnabled(sandboxEnabled);

        Task saved = taskService.create(task);
        sessionService.complete(sessionId);
        return saved;
    }
}
