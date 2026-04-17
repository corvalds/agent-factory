package com.agentfactory.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "task_events", indexes = {
    @Index(name = "idx_task_events_task_ts", columnList = "taskId, timestamp"),
    @Index(name = "idx_task_events_type_ts", columnList = "eventType, timestamp")
})
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Column(nullable = false)
    private Instant timestamp;

    private Integer durationMs;

    @Column(columnDefinition = "TEXT")
    private String data;

    @PrePersist
    void onCreate() {
        if (timestamp == null) timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
