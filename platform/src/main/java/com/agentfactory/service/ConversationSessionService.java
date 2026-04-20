package com.agentfactory.service;

import com.agentfactory.model.ConversationSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class ConversationSessionService {

    private final ConcurrentHashMap<String, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    void startReaper() {
        reaper.scheduleAtFixedRate(this::cleanExpired, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    void stopReaper() {
        reaper.shutdown();
    }

    public ConversationSession create() {
        var session = new ConversationSession();
        sessions.put(session.getId(), session);
        return session;
    }

    public ConversationSession get(String sessionId) {
        var session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        if (session.isExpired()) {
            sessions.remove(sessionId);
            throw new IllegalStateException("Session expired: " + sessionId);
        }
        return session;
    }

    public void complete(String sessionId) {
        var session = get(sessionId);
        session.setStatus("completed");
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    private void cleanExpired() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
