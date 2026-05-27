package com.agentfactory.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "feishu_sessions")
public class FeishuSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String openId;

    private String chatId;

    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeishuSessionState state = FeishuSessionState.IDLE;

    private Long taskId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOpenId() { return openId; }
    public void setOpenId(String openId) { this.openId = openId; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public FeishuSessionState getState() { return state; }
    public void setState(FeishuSessionState state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
