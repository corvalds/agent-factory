package com.agentfactory.controller;

import com.agentfactory.model.Task;
import com.agentfactory.model.TaskEvent;
import com.agentfactory.model.TaskStatus;
import com.agentfactory.service.CostCalculationService;
import com.agentfactory.service.TaskEventService;
import com.agentfactory.service.TaskExecutionService;
import com.agentfactory.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskExecutionService executionService;
    private final TaskEventService eventService;
    private final CostCalculationService costService;

    public TaskController(TaskService taskService,
                          TaskExecutionService executionService,
                          TaskEventService eventService,
                          CostCalculationService costService) {
        this.taskService = taskService;
        this.executionService = executionService;
        this.eventService = eventService;
        this.costService = costService;
    }

    @GetMapping
    public List<Task> list(@RequestParam(required = false) TaskStatus status) {
        if (status != null) {
            return taskService.findByStatus(status);
        }
        return taskService.findAll();
    }

    @GetMapping("/{id}")
    public Task get(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@RequestBody Task task) {
        return taskService.create(task);
    }

    @PostMapping("/clone/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public Task clone(@PathVariable Long id) {
        return taskService.clone(id);
    }

    @PatchMapping("/{id}/status")
    public Task updateStatus(@PathVariable Long id, @RequestBody StatusUpdate update) {
        return taskService.updateStatus(id, update.status());
    }

    @PostMapping("/{id}/execute")
    public Task execute(@PathVariable Long id) {
        return executionService.executeTask(id);
    }

    @GetMapping("/{id}/events")
    public List<TaskEvent> events(@PathVariable Long id) {
        return eventService.getEvents(id);
    }

    @GetMapping("/{id}/cost-estimate")
    public Map<String, Object> costEstimate(@PathVariable Long id) {
        Task task = taskService.findById(id);
        String inputText = String.join(" ",
            task.getBackground() != null ? task.getBackground() : "",
            task.getGoal() != null ? task.getGoal() : "",
            task.getAcceptanceCriteria() != null ? task.getAcceptanceCriteria() : ""
        );
        String modelId = task.getModelId();
        if (!costService.isModelKnown(modelId)) {
            return Map.of("available", false, "message", "Cost estimate unavailable for model: " + modelId);
        }
        BigDecimal estimate = costService.estimateCost(inputText, modelId);
        return Map.of("available", true, "estimatedCost", estimate.toPlainString(), "model", modelId);
    }

    record StatusUpdate(TaskStatus status) {}
}
