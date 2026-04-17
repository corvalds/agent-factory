package com.agentfactory.repository;

import com.agentfactory.model.Task;
import com.agentfactory.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByStatusOrderByCreatedAtDesc(TaskStatus status);
    List<Task> findAllByOrderByCreatedAtDesc();
    List<Task> findByModelIdIn(List<String> modelIds);
    boolean existsByModelIdInAndStatus(List<String> modelIds, TaskStatus status);
}
