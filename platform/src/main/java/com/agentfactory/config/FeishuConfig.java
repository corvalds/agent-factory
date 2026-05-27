package com.agentfactory.config;

import com.agentfactory.service.FeishuBotService;
import com.lark.oapi.Client;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@ConditionalOnExpression("!'${af.feishu.app-id:}'.isEmpty()")
public class FeishuConfig {

    private static final Logger log = LoggerFactory.getLogger(FeishuConfig.class);

    @Value("${af.feishu.app-id:}")
    private String appId;

    @Value("${af.feishu.app-secret:}")
    private String appSecret;

    @Bean
    public Client feishuClient() {
        return Client.newBuilder(appId, appSecret).build();
    }

    @Bean
    public EventDispatcher feishuEventDispatcher(FeishuBotService botService) {
        return EventDispatcher.newBuilder("", "")
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        botService.onMessageReceived(event);
                    }
                })
                .build();
    }

    @Bean
    public com.lark.oapi.ws.Client feishuWsClient(EventDispatcher eventDispatcher) {
        return new com.lark.oapi.ws.Client.Builder(appId, appSecret)
                .eventHandler(eventDispatcher)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startFeishuWebSocket(ApplicationReadyEvent event) {
        if (appId == null || appId.isBlank()) {
            return;
        }
        var wsClient = event.getApplicationContext().getBean(com.lark.oapi.ws.Client.class);
        Thread.startVirtualThread(() -> {
            log.info("Starting Feishu WebSocket connection...");
            try {
                wsClient.start();
            } catch (Exception e) {
                log.error("Feishu WebSocket connection failed: {}", e.getMessage(), e);
            }
        });
    }
}
