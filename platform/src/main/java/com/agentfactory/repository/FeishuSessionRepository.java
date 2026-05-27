package com.agentfactory.repository;

import com.agentfactory.model.FeishuSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeishuSessionRepository extends JpaRepository<FeishuSession, Long> {
    Optional<FeishuSession> findByOpenId(String openId);
    Optional<FeishuSession> findByTaskId(Long taskId);
}
