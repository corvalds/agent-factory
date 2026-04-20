package com.agentfactory.controller;

import com.agentfactory.model.TaskEvent;
import com.agentfactory.repository.TaskEventRepository;
import com.agentfactory.service.SseEmitterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class StreamController {

    private final SseEmitterService sseEmitterService;
    private final TaskEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public StreamController(SseEmitterService sseEmitterService,
                            TaskEventRepository eventRepository,
                            ObjectMapper objectMapper) {
        this.sseEmitterService = sseEmitterService;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        SseEmitter emitter = sseEmitterService.subscribe(id);

        if (lastEventId != null) {
            try {
                long afterId = Long.parseLong(lastEventId);
                List<TaskEvent> missed = eventRepository.findByTaskIdAndIdGreaterThanOrderByTimestampAsc(id, afterId);
                for (TaskEvent event : missed) {
                    String json = objectMapper.writeValueAsString(event);
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(event.getId()))
                            .name(event.getEventType().name().toLowerCase())
                            .data(json));
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }

        return emitter;
    }
}
