package com.agentfactory.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;
    private String background;
    private String goal;

    @Column(columnDefinition = "TEXT")
    private String acceptanceCriteria;

    @Column(nullable = false)
    private String agentType;

    private String modelId;
    private boolean sandboxEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @JsonIgnore
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Artifact> artifacts = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String error;

    private Instant startedAt;
    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Long parentTaskId;

    private String repoUrl;
    private String repoBranch;
    private String mrUrl;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public boolean isSandboxEnabled() { return sandboxEnabled; }
    public void setSandboxEnabled(boolean sandboxEnabled) { this.sandboxEnabled = sandboxEnabled; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public List<Artifact> getArtifacts() { return artifacts; }
    public void setArtifacts(List<Artifact> artifacts) { this.artifacts = artifacts; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(Long parentTaskId) { this.parentTaskId = parentTaskId; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getRepoBranch() { return repoBranch; }
    public void setRepoBranch(String repoBranch) { this.repoBranch = repoBranch; }
    public String getMrUrl() { return mrUrl; }
    public void setMrUrl(String mrUrl) { this.mrUrl = mrUrl; }
}
