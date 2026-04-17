package com.agentfactory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "agent_types")
public class AgentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;
    private String defaultModel;
    private boolean sandboxRequired;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public boolean isSandboxRequired() { return sandboxRequired; }
    public void setSandboxRequired(boolean sandboxRequired) { this.sandboxRequired = sandboxRequired; }
}
