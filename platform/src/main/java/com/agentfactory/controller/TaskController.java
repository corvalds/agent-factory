package com.agentfactory.controller;

import com.agentfactory.model.Task;
import com.agentfactory.model.TaskStatus;
import com.agentfactory.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
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

    record StatusUpdate(TaskStatus status) {}
}
