package com.agentfactory.repository;

import com.agentfactory.model.EventType;
import com.agentfactory.model.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {
    List<TaskEvent> findByTaskIdOrderByTimestampAsc(Long taskId);
    List<TaskEvent> findByTaskIdAndTimestampAfterOrderByTimestampAsc(Long taskId, Instant since);
    List<TaskEvent> findByTaskIdAndIdGreaterThanOrderByTimestampAsc(Long taskId, Long afterId);
    List<TaskEvent> findByEventTypeOrderByTimestampDesc(EventType eventType);
}
