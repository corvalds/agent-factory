package com.agentfactory.service;

import com.agentfactory.dto.DefineResponse;
import com.agentfactory.model.*;
import com.agentfactory.repository.FeishuSessionRepository;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@ConditionalOnBean(com.lark.oapi.Client.class)
public class FeishuBotService {

    private static final Logger log = LoggerFactory.getLogger(FeishuBotService.class);
    private static final Pattern MENTION_PLACEHOLDER = Pattern.compile("@_user_\\d+\\s*");

    private static final int DEDUP_CACHE_SIZE = 200;
    private final Map<String, Boolean> processedMessageIds = Collections.synchronizedMap(
            new LinkedHashMap<>(DEDUP_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DEDUP_CACHE_SIZE;
                }
            });


    private final FeishuSessionRepository sessionRepository;
    private final TaskDefinitionService taskDefinitionService;
    private final TaskExecutionService taskExecutionService;
    private final FeishuMessageService messageService;
    private final ObjectMapper objectMapper;

    @Value("${af.feishu.default-agent-type:general-purpose}")
    private String defaultAgentType;

    @Value("${af.feishu.llm-model:gpt-4o}")
    private String llmModel;

    @Value("${af.feishu.llm-api-key:}")
    private String llmApiKey;

    @Value("${af.feishu.llm-base-url:}")
    private String llmBaseUrl;

    public FeishuBotService(FeishuSessionRepository sessionRepository,
                            TaskDefinitionService taskDefinitionService,
                            TaskExecutionService taskExecutionService,
                            FeishuMessageService messageService,
                            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.taskDefinitionService = taskDefinitionService;
        this.taskExecutionService = taskExecutionService;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    public void onMessageReceived(P2MessageReceiveV1 event) {
        try {
            var message = event.getEvent().getMessage();
            String messageId = message.getMessageId();

            if (messageId != null && processedMessageIds.putIfAbsent(messageId, Boolean.TRUE) != null) {
                log.debug("Duplicate message ignored: {}", messageId);
                return;
            }

            var sender = event.getEvent().getSender();
            String openId = sender.getSenderId().getOpenId();
            String chatId = message.getChatId();
            String chatType = message.getChatType();
            String text = extractText(message.getContent());

            if (text == null || text.isBlank()) {
                return;
            }

            log.info("Feishu message from {} in {} ({}): {}", openId, chatId, chatType, text.substring(0, Math.min(50, text.length())));
            handleMessage(openId, chatId, chatType, text);
        } catch (Exception e) {
            log.error("Error processing Feishu message: {}", e.getMessage(), e);
        }
    }

    @Transactional
    protected void handleMessage(String openId, String chatId, String chatType, String text) {
        String replyTo = "group".equals(chatType) ? chatId : openId;
        String replyType = "group".equals(chatType) ? "chat_id" : "open_id";

        FeishuSession session = sessionRepository.findByOpenId(openId)
                .orElseGet(() -> {
                    var s = new FeishuSession();
                    s.setOpenId(openId);
                    s.setChatId(chatId);
                    s.setState(FeishuSessionState.IDLE);
                    return sessionRepository.save(s);
                });

        switch (session.getState()) {
            case IDLE -> handleIdle(session, text, replyTo, replyType);
            case DEFINING -> handleDefining(session, text, replyTo, replyType);
            case CONFIRMING -> handleConfirming(session, text, replyTo, replyType);
            case EXECUTING -> messageService.sendText(replyTo, replyType, "任务正在执行中，请稍候...");
        }
    }

    private void handleIdle(FeishuSession session, String text, String replyTo, String replyType) {
        if ("取消".equals(text.trim()) || "/cancel".equalsIgnoreCase(text.trim())) {
            messageService.sendText(replyTo, replyType, "当前没有进行中的任务。发送任何消息即可开始新任务。");
            return;
        }

        var convSession = taskDefinitionService.startConversation();
        session.setSessionId(convSession.getId());
        session.setState(FeishuSessionState.DEFINING);
        sessionRepository.save(session);

        processDefineMessage(session, text, replyTo, replyType);
    }

    private void handleDefining(FeishuSession session, String text, String replyTo, String replyType) {
        if ("取消".equals(text.trim()) || "/cancel".equalsIgnoreCase(text.trim())) {
            resetSession(session);
            messageService.sendText(replyTo, replyType, "已取消当前对话。");
            return;
        }
        processDefineMessage(session, text, replyTo, replyType);
    }

    private void processDefineMessage(FeishuSession session, String text, String replyTo, String replyType) {
        try {
            DefineResponse response = taskDefinitionService.processMessage(session.getSessionId(), text, llmModel, llmApiKey, llmBaseUrl);

            if (response.isComplete()) {
                session.setState(FeishuSessionState.CONFIRMING);
                sessionRepository.save(session);
                String summary = buildTaskSummary(response);
                messageService.sendText(replyTo, replyType,
                        summary + "\n\n回复「确认」开始执行，回复「取消」放弃。");
            } else {
                messageService.sendText(replyTo, replyType, response.reply());
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("Session not found")) {
                log.info("Conversation session lost for {}, recreating", session.getOpenId());
                var convSession = taskDefinitionService.startConversation();
                session.setSessionId(convSession.getId());
                sessionRepository.save(session);
                processDefineMessage(session, text, replyTo, replyType);
            } else {
                log.error("Error in task definition for {}: {}", session.getOpenId(), e.getMessage(), e);
                resetSession(session);
                messageService.sendText(replyTo, replyType, "处理出错，请重新发送消息开始。");
            }
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("expired")) {
                log.info("Session expired for {}, resetting", session.getOpenId());
                resetSession(session);
                messageService.sendText(replyTo, replyType, "会话已过期，请重新发送消息开始新任务。");
            } else {
                log.error("Error in task definition for {}: {}", session.getOpenId(), e.getMessage(), e);
                resetSession(session);
                messageService.sendText(replyTo, replyType, "处理出错，请重新发送消息开始。");
            }
        } catch (Exception e) {
            log.error("Error in task definition for {}: {}", session.getOpenId(), e.getMessage(), e);
            resetSession(session);
            messageService.sendText(replyTo, replyType, "处理出错，请重新发送消息开始。");
        }
    }

    private void handleConfirming(FeishuSession session, String text, String replyTo, String replyType) {
        String trimmed = text.trim();
        if ("确认".equals(trimmed) || "是".equals(trimmed) || "yes".equalsIgnoreCase(trimmed) || "confirm".equalsIgnoreCase(trimmed)) {
            try {
                Task task = taskDefinitionService.confirmDefinition(session.getSessionId(), defaultAgentType, llmModel, false, null, null);
                session.setTaskId(task.getId());
                session.setState(FeishuSessionState.EXECUTING);
                sessionRepository.save(session);

                messageService.sendText(replyTo, replyType, "✓ 任务已创建，开始执行...");
                taskExecutionService.executeTask(task.getId());
            } catch (Exception e) {
                log.error("Error confirming task for {}: {}", session.getOpenId(), e.getMessage(), e);
                resetSession(session);
                messageService.sendText(replyTo, replyType, "任务创建失败: " + e.getMessage());
            }
        } else if ("取消".equals(trimmed) || "否".equals(trimmed) || "no".equalsIgnoreCase(trimmed) || "/cancel".equalsIgnoreCase(trimmed)) {
            resetSession(session);
            messageService.sendText(replyTo, replyType, "已取消。发送新消息可开始新任务。");
        } else {
            messageService.sendText(replyTo, replyType, "请回复「确认」开始执行，或「取消」放弃。");
        }
    }

    private void resetSession(FeishuSession session) {
        session.setState(FeishuSessionState.IDLE);
        session.setSessionId(null);
        session.setTaskId(null);
        sessionRepository.save(session);
    }

    private String extractText(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            if (node.has("text")) {
                String text = node.get("text").asText();
                return MENTION_PLACEHOLDER.matcher(text).replaceAll("").trim();
            }
            return content;
        } catch (Exception e) {
            return content;
        }
    }

    private String buildTaskSummary(DefineResponse response) {
        if (response.structured() == null) {
            return "任务定义已完成。";
        }
        var def = response.structured();
        StringBuilder sb = new StringBuilder();
        if (def.containsKey("goal")) {
            sb.append("目标: ").append(def.get("goal")).append("\n");
        }
        if (def.containsKey("background")) {
            sb.append("背景: ").append(def.get("background")).append("\n");
        }
        if (def.containsKey("acceptance_criteria")) {
            sb.append("验收标准: ").append(def.get("acceptance_criteria"));
        }
        return sb.toString();
    }
}
