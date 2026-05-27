package com.agentfactory.service;

import com.agentfactory.event.TaskEventPublished;
import com.agentfactory.model.FeishuSession;
import com.agentfactory.model.FeishuSessionState;
import com.agentfactory.model.TaskEvent;
import com.agentfactory.repository.FeishuSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@ConditionalOnBean(com.lark.oapi.Client.class)
public class FeishuNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(FeishuNotificationListener.class);

    private final FeishuSessionRepository sessionRepository;
    private final FeishuMessageService messageService;
    private final ObjectMapper objectMapper;

    public FeishuNotificationListener(FeishuSessionRepository sessionRepository,
                                       FeishuMessageService messageService,
                                       ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    @Async
    @EventListener
    @Transactional
    public void onTaskEvent(TaskEventPublished event) {
        TaskEvent taskEvent = event.getTaskEvent();
        Optional<FeishuSession> sessionOpt = sessionRepository.findByTaskId(taskEvent.getTaskId());
        if (sessionOpt.isEmpty()) {
            return;
        }

        FeishuSession session = sessionOpt.get();
        if (session.getState() != FeishuSessionState.EXECUTING) {
            return;
        }

        switch (taskEvent.getEventType()) {
            case COMPLETION -> handleCompletion(session, taskEvent);
            case ERROR -> handleError(session, taskEvent);
            case CLARIFICATION -> handleClarification(session, taskEvent);
            default -> {}
        }
    }

    private void handleCompletion(FeishuSession session, TaskEvent taskEvent) {
        String result = extractField(taskEvent.getData(), "final_result");
        String message = result != null && !result.isBlank()
                ? "✓ 任务完成\n\n" + truncate(result, 3000)
                : "✓ 任务已完成。";

        String chatId = session.getChatId();
        String replyTo = chatId != null && chatId.startsWith("oc_") ? chatId : session.getOpenId();
        String replyType = chatId != null && chatId.startsWith("oc_") ? "chat_id" : "open_id";
        messageService.sendText(replyTo, replyType, message);

        session.setState(FeishuSessionState.IDLE);
        session.setTaskId(null);
        session.setSessionId(null);
        sessionRepository.save(session);
    }

    private void handleError(FeishuSession session, TaskEvent taskEvent) {
        String errorMsg = extractField(taskEvent.getData(), "message");
        boolean retryable = "true".equals(extractField(taskEvent.getData(), "retryable"));
        if (retryable) {
            return;
        }

        String message = "✗ 任务执行失败" + (errorMsg != null ? ": " + errorMsg : "");

        String chatId = session.getChatId();
        String replyTo = chatId != null && chatId.startsWith("oc_") ? chatId : session.getOpenId();
        String replyType = chatId != null && chatId.startsWith("oc_") ? "chat_id" : "open_id";
        messageService.sendText(replyTo, replyType, message);

        session.setState(FeishuSessionState.IDLE);
        session.setTaskId(null);
        session.setSessionId(null);
        sessionRepository.save(session);
    }

    private void handleClarification(FeishuSession session, TaskEvent taskEvent) {
        String message = extractField(taskEvent.getData(), "message");
        String text = "❓ " + (message != null ? message : "需要更多信息，请补充说明。");

        String chatId = session.getChatId();
        String replyTo = chatId != null && chatId.startsWith("oc_") ? chatId : session.getOpenId();
        String replyType = chatId != null && chatId.startsWith("oc_") ? "chat_id" : "open_id";
        messageService.sendText(replyTo, replyType, text);

        session.setState(FeishuSessionState.DEFINING);
        sessionRepository.save(session);
    }

    private String extractField(String json, String field) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode value = node.get(field);
            return value != null ? value.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
