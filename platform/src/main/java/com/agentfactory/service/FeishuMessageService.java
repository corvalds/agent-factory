package com.agentfactory.service;

import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(Client.class)
public class FeishuMessageService {

    private static final Logger log = LoggerFactory.getLogger(FeishuMessageService.class);
    private final Client client;

    public FeishuMessageService(Client client) {
        this.client = client;
    }

    public void sendText(String receiveId, String receiveIdType, String text) {
        try {
            String content = "{\"text\":\"" + escapeJson(text) + "\"}";
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType(receiveIdType)
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(receiveId)
                            .msgType("text")
                            .content(content)
                            .build())
                    .build();
            CreateMessageResp resp = client.im().v1().message().create(req);
            if (!resp.success()) {
                log.error("Failed to send Feishu message: code={}, msg={}", resp.getCode(), resp.getMsg());
            }
        } catch (Exception e) {
            log.error("Error sending Feishu message to {}: {}", receiveId, e.getMessage(), e);
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
