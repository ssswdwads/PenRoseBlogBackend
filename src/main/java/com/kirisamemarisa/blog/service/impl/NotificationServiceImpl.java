package com.kirisamemarisa.blog.service.impl;

import com.kirisamemarisa.blog.dto.NotificationDTO;
import com.kirisamemarisa.blog.events.NotificationEventPublisher;
import com.kirisamemarisa.blog.events.NotificationMessage;
import com.kirisamemarisa.blog.events.RabbitNotificationBridge;
import com.kirisamemarisa.blog.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationEventPublisher publisher;

    /**
     * 可选：通过 RabbitMQ 暂存通知的桥接组件。
     * 如果类路径上没有 RabbitMQ 支持或未配置，则为 null，本地直接 SSE 推送。
     */
    @Autowired(required = false)
    private RabbitNotificationBridge rabbitBridge;

    public NotificationServiceImpl(NotificationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public SseEmitter subscribe(Long userId, Object initialPayload) {
        return publisher.subscribe(userId, initialPayload);
    }

    /**
     * 统一的通知发送入口：
     * - 优先通过 RabbitMQ 暂存（异步投递）。
     * - 若 RabbitMQ 不可用或发送失败，则回退到本地 SSE 推送。
     */
    @Override
    public void sendNotification(Long userId, NotificationDTO payload) {
        if (payload == null || userId == null) return;

        // RabbitMQ 优先
        if (rabbitBridge != null) {
            try {
                NotificationMessage m = new NotificationMessage();
                m.setReceiverId(userId);
                m.setSenderId(payload.getSenderId());
                m.setRequestId(payload.getRequestId());
                m.setType(payload.getType());
                m.setMessage(payload.getMessage());
                m.setStatus(payload.getStatus());
                m.setCreatedAt(payload.getCreatedAt());
                m.setReferenceId(payload.getReferenceId());
                m.setReferenceExtraId(payload.getReferenceExtraId());
                rabbitBridge.publish(m);
                return;
            } catch (Exception ex) {
                logger.warn(
                        "Failed to publish notification via Rabbit bridge: {}. Falling back to local send",
                        ex.toString()
                );
            }
        }

        // fallback 到本地 SSE 推送
        publisher.sendNotification(userId, payload);
    }

    @Override
    public boolean isOnline(Long userId) {
        return publisher.isOnline(userId);
    }
}