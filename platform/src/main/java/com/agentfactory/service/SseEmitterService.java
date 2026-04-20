package com.agentfactory.service;

import com.agentfactory.model.TaskEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseEmitterService {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterService.class);
    private final ConcurrentHashMap<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitterService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe(Long taskId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        emitter.onError(e -> removeEmitter(taskId, emitter));

        return emitter;
    }

    public void emit(Long taskId, TaskEvent event) {
        List<SseEmitter> taskEmitters = emitters.get(taskId);
        if (taskEmitters == null || taskEmitters.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(event);
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .id(String.valueOf(event.getId()))
                    .name(event.getEventType().name().toLowerCase())
                    .data(json);

            for (SseEmitter emitter : taskEmitters) {
                try {
                    emitter.send(builder);
                } catch (Exception e) {
                    removeEmitter(taskId, emitter);
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize event for task {}: {}", taskId, e.getMessage());
        }
    }

    public void completeAll(Long taskId) {
        List<SseEmitter> taskEmitters = emitters.remove(taskId);
        if (taskEmitters != null) {
            taskEmitters.forEach(SseEmitter::complete);
        }
    }

    private void removeEmitter(Long taskId, SseEmitter emitter) {
        List<SseEmitter> taskEmitters = emitters.get(taskId);
        if (taskEmitters != null) {
            taskEmitters.remove(emitter);
            if (taskEmitters.isEmpty()) {
                emitters.remove(taskId);
            }
        }
    }
}
