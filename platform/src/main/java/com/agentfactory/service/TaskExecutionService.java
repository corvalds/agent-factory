package com.agentfactory.service;

import com.agentfactory.dto.ExecuteRequest;
import com.agentfactory.dto.ExecuteResponse;
import com.agentfactory.model.AgentType;
import com.agentfactory.model.Task;
import com.agentfactory.model.TaskStatus;
import com.agentfactory.repository.AgentTypeRepository;
import com.agentfactory.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final TaskRepository taskRepository;
    private final AgentTypeRepository agentTypeRepository;
    private final AgentServiceClient agentClient;
    private final TaskEventService eventService;
    private final CostCalculationService costService;
    private final ProviderService providerService;
    private final SseEmitterService sseEmitterService;
    private final SandboxService sandboxService;

    public TaskExecutionService(TaskRepository taskRepository,
                                 AgentTypeRepository agentTypeRepository,
                                 AgentServiceClient agentClient,
                                 TaskEventService eventService,
                                 CostCalculationService costService,
                                 ProviderService providerService,
                                 SseEmitterService sseEmitterService,
                                 SandboxService sandboxService) {
        this.taskRepository = taskRepository;
        this.agentTypeRepository = agentTypeRepository;
        this.agentClient = agentClient;
        this.eventService = eventService;
        this.costService = costService;
        this.providerService = providerService;
        this.sseEmitterService = sseEmitterService;
        this.sandboxService = sandboxService;
    }

    public Task executeTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.getStatus() != TaskStatus.PENDING) {
            throw new IllegalStateException("Task is not in PENDING status: " + task.getStatus());
        }

        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(Instant.now());
        taskRepository.save(task);

        executor.submit(() -> runTask(task.getId()));
        return task;
    }

    private void runTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task disappeared: " + taskId));

        boolean useSandbox = shouldUseSandbox(task);

        try {
            String apiKey = resolveApiKey(task.getModelId());
            ExecuteResponse response;

            if (useSandbox) {
                response = runSandboxed(task, apiKey);
            } else {
                response = runDirect(task, apiKey);
            }

            BigDecimal totalCost = processSteps(task, response);

            if ("completed".equals(response.status())) {
                task.setStatus(TaskStatus.COMPLETED);
                task.setResult(response.result());
            } else {
                task.setStatus(TaskStatus.FAILED);
                task.setError(response.result());
            }
            task.setCompletedAt(Instant.now());
            taskRepository.save(task);

            eventService.recordCompletionEvent(
                task.getId(), response.result(), response.totalTokens(),
                totalCost.toPlainString(), response.steps().size()
            );
            sseEmitterService.completeAll(task.getId());

        } catch (Exception e) {
            log.error("Task {} execution failed: {}", task.getId(), e.getMessage(), e);
            task.setStatus(TaskStatus.FAILED);
            task.setError(e.getMessage());
            task.setCompletedAt(Instant.now());
            taskRepository.save(task);
            eventService.recordErrorEvent(task.getId(), e.getClass().getSimpleName(), e.getMessage(), false);
            sseEmitterService.completeAll(task.getId());
        }
    }

    private boolean shouldUseSandbox(Task task) {
        boolean agentRequires = agentTypeRepository.findAll().stream()
                .filter(at -> at.getName().equals(task.getAgentType()))
                .findFirst()
                .map(AgentType::isSandboxRequired)
                .orElse(false);
        return agentRequires || task.isSandboxEnabled();
    }

    private ExecuteResponse runDirect(Task task, String apiKey) {
        ExecuteRequest request = new ExecuteRequest(
            task.getBackground() != null ? task.getBackground() : "",
            task.getGoal() != null ? task.getGoal() : task.getName(),
            task.getAcceptanceCriteria() != null ? task.getAcceptanceCriteria() : "",
            task.getAgentType(),
            task.getModelId() != null ? task.getModelId() : "gpt-4o",
            apiKey
        );
        return agentClient.execute(request);
    }

    private ExecuteResponse runSandboxed(Task task, String apiKey) {
        var ctx = sandboxService.createSandbox(task.getId(), task.getAgentType());
        try {
            sandboxService.writeInput(ctx, Map.of(
                "background", task.getBackground() != null ? task.getBackground() : "",
                "goal", task.getGoal() != null ? task.getGoal() : task.getName(),
                "acceptance_criteria", task.getAcceptanceCriteria() != null ? task.getAcceptanceCriteria() : "",
                "agent_type", task.getAgentType(),
                "model", task.getModelId() != null ? task.getModelId() : "gpt-4o"
            ), apiKey);

            Map<String, Object> output = sandboxService.pollForOutput(ctx, 300);

            String result = (String) output.getOrDefault("result", "");
            String status = (String) output.getOrDefault("status", "failed");
            int totalTokens = output.get("total_tokens") instanceof Number n ? n.intValue() : 0;
            List<Map<String, Object>> steps = output.get("steps") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();

            return new ExecuteResponse(result, steps, totalTokens, status);
        } finally {
            sandboxService.destroySandbox(ctx);
        }
    }

    private BigDecimal processSteps(Task task, ExecuteResponse response) {
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Map<String, Object> step : response.steps()) {
            int stepNum = step.get("step") instanceof Number n ? n.intValue() : 0;
            String phase = (String) step.getOrDefault("phase", "act");
            String output = (String) step.getOrDefault("output", "");
            int tokensIn = step.get("tokens_in") instanceof Number n ? n.intValue() : 0;
            int tokensOut = step.get("tokens_out") instanceof Number n ? n.intValue() : 0;
            int durationMs = step.get("duration_ms") instanceof Number n ? n.intValue() : 0;

            eventService.recordStepEvent(task.getId(), stepNum, phase, "", output, durationMs);

            if (tokensIn > 0 || tokensOut > 0) {
                BigDecimal stepCost = costService.calculateCost(tokensIn, tokensOut, task.getModelId());
                totalCost = totalCost.add(stepCost);
                eventService.recordCostEvent(task.getId(), tokensIn, tokensOut, task.getModelId(), stepCost.toPlainString(), durationMs);
            }

            if ("error".equals(phase)) {
                String errorType = (String) step.getOrDefault("error_type", "unknown");
                String message = (String) step.getOrDefault("message", "");
                eventService.recordErrorEvent(task.getId(), errorType, message, true);
            }
        }
        return totalCost;
    }

    private String resolveApiKey(String modelId) {
        if (modelId == null) return null;
        var providers = providerService.findActive();
        for (var provider : providers) {
            if (provider.getModels() != null) {
                List<String> models = Arrays.stream(provider.getModels().split(","))
                        .map(String::trim).toList();
                if (models.contains(modelId)) {
                    return providerService.decryptApiKey(provider.getId());
                }
            }
        }
        return null;
    }
}
