package com.agentfactory.repository;

import com.agentfactory.model.AgentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTypeRepository extends JpaRepository<AgentType, Long> {
}
