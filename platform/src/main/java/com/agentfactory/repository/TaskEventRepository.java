package com.agentfactory.repository;

import com.agentfactory.model.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {
    List<TaskEvent> findByTaskIdOrderByTimestampAsc(Long taskId);
}
