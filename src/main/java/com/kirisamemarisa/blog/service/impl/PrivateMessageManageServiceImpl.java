//java
//撤回：确认当前用户是 sender，然后在所有相关 PrivateMessageStatus 上标记 recalled = true。
//删除：仅在当前用户自己的 PrivateMessageStatus 上标记 deletedForUser = true。
//获取会话时：先用原来的 PrivateMessageService.conversation(...) 拿到所有消息，再根据 PrivateMessageStatus 过滤（对当前用户已删除的剔除），并生成视图 DTO

package com.kirisamemarisa.blog.service.impl;

import com.kirisamemarisa.blog.dto.PrivateMessageDTO;
import com.kirisamemarisa.blog.dto.PrivateMessageViewDTO;
import com.kirisamemarisa.blog.events.MessageEventPublisher;
import com.kirisamemarisa.blog.mapper.PrivateMessageViewMapper;
import com.kirisamemarisa.blog.model.PrivateMessage;
import com.kirisamemarisa.blog.model.PrivateMessageStatus;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.repository.PrivateMessageRepository;
import com.kirisamemarisa.blog.repository.PrivateMessageStatusRepository;
import com.kirisamemarisa.blog.service.PrivateMessageManageService;
import com.kirisamemarisa.blog.service.PrivateMessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PrivateMessageManageServiceImpl implements PrivateMessageManageService {

    private static final long RECALL_LIMIT_MINUTES = 2L;

    private final PrivateMessageRepository messageRepository;
    private final PrivateMessageStatusRepository statusRepository;
    private final PrivateMessageService privateMessageService;
    private final MessageEventPublisher publisher;

    public PrivateMessageManageServiceImpl(PrivateMessageRepository messageRepository,
                                           PrivateMessageStatusRepository statusRepository,
                                           PrivateMessageService privateMessageService,
                                           MessageEventPublisher publisher) {
        this.messageRepository = messageRepository;
        this.statusRepository = statusRepository;
        this.privateMessageService = privateMessageService;
        this.publisher = publisher;
    }

    // 轻量 DTO 映射（只需基本字段，供 SSE 会话列表用）
    private PrivateMessageDTO toDTO(PrivateMessage msg) {
        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setId(msg.getId());
        dto.setSenderId(msg.getSender() != null ? msg.getSender().getId() : null);
        dto.setReceiverId(msg.getReceiver() != null ? msg.getReceiver().getId() : null);
        dto.setText(msg.getText());
        dto.setMediaUrl(msg.getMediaUrl());
        dto.setType(msg.getType());
        dto.setCreatedAt(msg.getCreatedAt());
        return dto;
    }

    @Override
    public void recallMessage(User currentUser, Long messageId) {
        PrivateMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));

        // 只能撤回自己发的
        if (message.getSender() == null
                || !Objects.equals(message.getSender().getId(), currentUser.getId())) {
            throw new IllegalStateException("只能撤回自己发送的消息");
        }

        // 时间限制：发送两分钟内才允许撤回
        Instant createdAt = message.getCreatedAt();
        if (createdAt != null) {
            long minutes = Duration.between(createdAt, Instant.now()).toMinutes();
            if (minutes > RECALL_LIMIT_MINUTES) {
                throw new IllegalStateException("消息发出超过两分钟");
            }
        } else {
            // 如果 createdAt 为空，为了安全起见视为不可撤回
            throw new IllegalStateException("消息发出超过两分钟");
        }

        // 找到所有与该消息相关的状态记录（包括发送方和接收方）
        List<PrivateMessageStatus> allStatus = statusRepository.findByMessage(message);

        // 统一：确保 sender/receiver 都有一条状态记录
        User sender = message.getSender();
        User receiver = message.getReceiver();

        // 现有记录先全部标记撤回
        if (!allStatus.isEmpty()) {
            for (PrivateMessageStatus s : allStatus) {
                s.setRecalled(true);
            }
            statusRepository.saveAll(allStatus);
        }

        // 检查并为缺失的一方补记录（关键修复：即使已有部分记录，也要补齐另一方）
        boolean hasSender = allStatus.stream().anyMatch(s -> s.getUser() != null && s.getUser().getId().equals(sender != null ? sender.getId() : null));
        boolean hasReceiver = allStatus.stream().anyMatch(s -> s.getUser() != null && s.getUser().getId().equals(receiver != null ? receiver.getId() : null));

        if (sender != null && !hasSender) {
            PrivateMessageStatus s = new PrivateMessageStatus();
            s.setMessage(message);
            s.setUser(sender);
            s.setRecalled(true);
            s.setDeletedForUser(false);
            statusRepository.save(s);
        }
        if (receiver != null && !hasReceiver) {
            PrivateMessageStatus s = new PrivateMessageStatus();
            s.setMessage(message);
            s.setUser(receiver);
            s.setRecalled(true);
            s.setDeletedForUser(false);
            statusRepository.save(s);
        }

        // 新增：撤回成功后广播会话更新（双方都能即时收到）
        try {
            User other = Objects.equals(message.getSender().getId(), currentUser.getId())
                    ? message.getReceiver() : message.getSender();
            if (other != null) {
                List<PrivateMessageDTO> conversation = privateMessageService
                        .conversation(currentUser, other)
                        .stream().map(this::toDTO).toList();
                publisher.broadcast(currentUser.getId(), other.getId(), conversation);
            }
        } catch (Exception ignore) {
            // 保持最小侵入：广播失败不影响主流程
        }
    }

    @Override
    public void deleteMessageForUser(User currentUser, Long messageId) {
        PrivateMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));

        PrivateMessageStatus status = statusRepository
                .findByMessageAndUser(message, currentUser)
                .orElseGet(() -> {
                    PrivateMessageStatus s = new PrivateMessageStatus();
                    s.setMessage(message);
                    s.setUser(currentUser);
                    return s;
                });

        status.setDeletedForUser(true);
        statusRepository.save(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrivateMessageViewDTO> getConversationView(User currentUser, User otherUser) {
        // 复用原有的会话查询逻辑
        List<PrivateMessage> raw = privateMessageService.conversation(currentUser, otherUser);
        if (raw.isEmpty()) return Collections.emptyList();

        // 批量拉取该用户对应的状态
        List<PrivateMessageStatus> statusList = statusRepository.findByMessageInAndUser(raw, currentUser);
        Map<Long, PrivateMessageStatus> statusMap = statusList.stream()
                .filter(s -> s.getMessage() != null && s.getMessage().getId() != null)
                .collect(Collectors.toMap(s -> s.getMessage().getId(), s -> s));

        List<PrivateMessageViewDTO> result = new ArrayList<>();
        for (PrivateMessage m : raw) {
            PrivateMessageStatus st = statusMap.get(m.getId());
            if (st != null && st.isDeletedForUser()) {
                // 当前用户本地删除的消息不再展示
                continue;
            }
            PrivateMessageViewDTO dto = PrivateMessageViewMapper.toViewDTO(m, st, currentUser);
            result.add(dto);
        }
        return result;
    }
}
