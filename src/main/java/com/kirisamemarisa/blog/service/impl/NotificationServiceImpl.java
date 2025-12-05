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

    // optional bridge; may be null if RabbitMQ support is not on the classpath or not configured
    @Autowired(required = false)
    private RabbitNotificationBridge rabbitBridge;

    public NotificationServiceImpl(NotificationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public SseEmitter subscribe(Long userId, Object initialPayload) {
        return publisher.subscribe(userId, initialPayload);
    }

    @Override
    public void sendNotification(Long userId, NotificationDTO payload) {
        if (payload == null || userId == null) return;
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
                logger.warn("Failed to publish notification via Rabbit bridge: {}. Falling back to local send", ex.toString());
            }
        }
        // fallback to local delivery
        publisher.sendNotification(userId, payload);
    }

    @Override
    public boolean isOnline(Long userId) {
        return publisher.isOnline(userId);
    }
}