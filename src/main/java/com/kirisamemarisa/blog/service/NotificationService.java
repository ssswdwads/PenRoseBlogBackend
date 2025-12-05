package com.kirisamemarisa.blog.service;

import com.kirisamemarisa.blog.dto.NotificationDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 通知服务统一接口。
 * 所有需要通知的业务（文章点赞、评论、楼中楼、私信等）都通过这里发。
 */
public interface NotificationService {
    /**
     * SSE 订阅：前端通过该连接实时接收推送。
     */
    SseEmitter subscribe(Long userId, Object initialPayload);

    /**
     * 发送一条通知给 userId 对应的用户。
     * 具体实现中会优先通过 RabbitMQ 暂存，再通过 SSE 下发。
     */
    void sendNotification(Long userId, NotificationDTO payload);

    /**
     * 用户是否有活跃的 SSE 连接。
     */
    boolean isOnline(Long userId);
}