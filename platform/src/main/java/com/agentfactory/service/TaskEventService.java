package com.agentfactory.service;

import com.agentfactory.model.EventType;
import com.agentfactory.model.TaskEvent;
import com.agentfactory.repository.TaskEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class TaskEventService {

    private final TaskEventRepository eventRepository;
    private final SseEmitterService sseEmitterService;
    private final ObjectMapper objectMapper;

    public TaskEventService(TaskEventRepository eventRepository,
                            SseEmitterService sseEmitterService,
                            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.sseEmitterService = sseEmitterService;
        this.objectMapper = objectMapper;
    }

    public List<TaskEvent> getEvents(Long taskId) {
        return eventRepository.findByTaskIdOrderByTimestampAsc(taskId);
    }

    public List<TaskEvent> getEventsSince(Long taskId, Instant since) {
        return eventRepository.findByTaskIdAndTimestampAfterOrderByTimestampAsc(taskId, since);
    }

    public TaskEvent recordStepEvent(Long taskId, int stepNumber, String phase, String input, String output, int durationMs) {
        return record(taskId, EventType.STEP, durationMs, Map.of(
            "step_number", stepNumber,
            "phase", phase,
            "input", input != null ? input : "",
            "output", output != null ? output : ""
        ));
    }

    public TaskEvent recordCostEvent(Long taskId, int tokensIn, int tokensOut, String model, String costUsd, int latencyMs) {
        return record(taskId, EventType.COST, latencyMs, Map.of(
            "tokens_in", tokensIn,
            "tokens_out", tokensOut,
            "model", model,
            "cost_usd", costUsd,
            "latency_ms", latencyMs
        ));
    }

    public TaskEvent recordErrorEvent(Long taskId, String errorType, String message, boolean retryable) {
        return record(taskId, EventType.ERROR, null, Map.of(
            "error_type", errorType,
            "message", message,
            "retryable", retryable
        ));
    }

    public TaskEvent recordCompletionEvent(Long taskId, String finalResult, int totalTokens, String totalCostUsd, int totalSteps) {
        return record(taskId, EventType.COMPLETION, null, Map.of(
            "final_result", finalResult != null ? finalResult : "",
            "total_tokens", totalTokens,
            "total_cost_usd", totalCostUsd,
            "total_steps", totalSteps
        ));
    }

    private TaskEvent record(Long taskId, EventType type, Integer durationMs, Map<String, Object> data) {
        TaskEvent event = new TaskEvent();
        event.setTaskId(taskId);
        event.setEventType(type);
        event.setDurationMs(durationMs);
        try {
            event.setData(objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            event.setData("{}");
        }
        TaskEvent saved = eventRepository.save(event);
        sseEmitterService.emit(taskId, saved);
        return saved;
    }
}
