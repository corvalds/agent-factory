package com.agentfactory.service;

import com.agentfactory.model.Task;
import com.agentfactory.model.TaskStatus;
import com.agentfactory.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Task> findAll() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Task> findByStatus(TaskStatus status) {
        return taskRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
    }

    public Task create(Task task) {
        task.setStatus(TaskStatus.PENDING);
        return taskRepository.save(task);
    }

    public Task clone(Long id) {
        Task original = findById(id);
        Task clone = new Task();
        clone.setName(original.getName());
        clone.setDescription(original.getDescription());
        clone.setBackground(original.getBackground());
        clone.setGoal(original.getGoal());
        clone.setAcceptanceCriteria(original.getAcceptanceCriteria());
        clone.setAgentType(original.getAgentType());
        clone.setModelId(original.getModelId());
        clone.setSandboxEnabled(original.isSandboxEnabled());
        clone.setStatus(TaskStatus.PENDING);
        return taskRepository.save(clone);
    }

    public Task updateStatus(Long id, TaskStatus status) {
        Task task = findById(id);
        task.setStatus(status);
        if (status == TaskStatus.RUNNING) {
            task.setStartedAt(java.time.Instant.now());
        } else if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
            task.setCompletedAt(java.time.Instant.now());
        }
        return taskRepository.save(task);
    }
}
