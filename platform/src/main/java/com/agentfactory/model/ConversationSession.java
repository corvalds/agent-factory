package com.agentfactory.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConversationSession {

    private final String id;
    private final List<Map<String, String>> messages;
    private String status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int turnCount;
    private Map<String, String> structuredDefinition;

    public ConversationSession() {
        this.id = UUID.randomUUID().toString();
        this.messages = new ArrayList<>();
        this.status = "active";
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(3600);
        this.turnCount = 0;
    }

    public String getId() { return id; }
    public List<Map<String, String>> getMessages() { return messages; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getTurnCount() { return turnCount; }
    public void incrementTurnCount() { this.turnCount++; }
    public Map<String, String> getStructuredDefinition() { return structuredDefinition; }
    public void setStructuredDefinition(Map<String, String> structuredDefinition) { this.structuredDefinition = structuredDefinition; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isAtTurnLimit() {
        return turnCount >= 10;
    }

    public void addMessage(String role, String content) {
        messages.add(Map.of("role", role, "content", content));
    }
}
