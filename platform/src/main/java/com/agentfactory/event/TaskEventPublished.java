package com.agentfactory.event;

import com.agentfactory.model.TaskEvent;
import org.springframework.context.ApplicationEvent;

public class TaskEventPublished extends ApplicationEvent {

    private final TaskEvent taskEvent;

    public TaskEventPublished(Object source, TaskEvent taskEvent) {
        super(source);
        this.taskEvent = taskEvent;
    }

    public TaskEvent getTaskEvent() {
        return taskEvent;
    }
}
