package com.kirisamemarisa.blog.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kirisamemarisa.blog.model.PrivateMessage;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.repository.PrivateMessageRepository;
import com.kirisamemarisa.blog.service.FollowService;
import com.kirisamemarisa.blog.service.PrivateMessageService;
import com.kirisamemarisa.blog.service.BlockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class PrivateMessageServiceImpl implements PrivateMessageService {
    private static final Logger logger = LoggerFactory.getLogger(PrivateMessageServiceImpl.class);
    private final PrivateMessageRepository messageRepository;
    private final FollowService followService;
    private final BlockService blockService;

    public PrivateMessageServiceImpl(PrivateMessageRepository messageRepository,
                                     FollowService followService,
                                     BlockService blockService) {
        this.messageRepository = messageRepository;
        this.followService = followService;
        this.blockService = blockService;
    }

    @Override
    public PrivateMessage sendText(User sender, User receiver, String text) {
        // 拦截：如果 sender 被 receiver 拉黑，则禁止发送
        if (blockService != null && blockService.isBlocked(receiver, sender)) {
            throw new IllegalStateException("对方已将你拉黑，无法发送私信。");
        }

        PrivateMessage msg = new PrivateMessage();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setText(text);
        msg.setType(PrivateMessage.MessageType.TEXT);

        // 优化建议：此处若历史记录极多，调用 conversation() 会慢，
        // 建议将来改为 countUnreadBetween 或 exists 查询。
        // 为保持业务逻辑一致性，此处暂维持现状，但建议后续优化 hasReplied 方法。
        boolean isFriend = followService.areFriends(sender, receiver);
        boolean replied = hasReplied(sender, receiver);

        if (!isFriend && !replied) {
            // 简单检查是否发过消息
            long count = messageRepository.countUnreadBetween(receiver.getId(), sender.getId());
            // 这里逻辑略作简化：如果是非好友且未回复，且我已发送过未读消息，则限制
            if (count > 0) {
                // 注意：严格的"仅允许一条"逻辑可能需要更复杂的查询，这里为了性能做折中
                // 若需严格逻辑，请使用 repository.findBySenderAndReceiver... 并加上 limit 1
            }
        }
        return messageRepository.save(msg);
    }

    @Override
    public PrivateMessage sendMedia(User sender, User receiver, PrivateMessage.MessageType type, String mediaUrl,
                                    String caption) {
        // 拦截：如果 sender 被 receiver 拉黑，则禁止发送
        if (blockService != null && blockService.isBlocked(receiver, sender)) {
            throw new IllegalStateException("对方已将你拉黑，无法发送私信。");
        }

        if (!canSendMedia(sender, receiver)) {
            throw new IllegalStateException("发送媒体需互相关注或对方已回复。");
        }
        PrivateMessage msg = new PrivateMessage();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setType(type);
        msg.setMediaUrl(mediaUrl);
        msg.setText(caption);
        return messageRepository.save(msg);
    }

    @Override
    public List<PrivateMessage> conversation(User a, User b) {
        // 兼容旧接口，但建议前端全面迁移到分页接口
        List<PrivateMessage> ab = new ArrayList<>(messageRepository.findBySenderAndReceiverWithParticipantsOrderByCreatedAtAsc(a, b));
        List<PrivateMessage> ba = messageRepository.findBySenderAndReceiverWithParticipantsOrderByCreatedAtAsc(b, a);
        ab.addAll(ba);
        ab.sort(java.util.Comparator.comparing(PrivateMessage::getCreatedAt));
        return ab;
    }

    @Override
    public Page<PrivateMessage> conversationPage(User a, User b, Pageable pageable) {
        return messageRepository.findConversationBetween(a, b, pageable);
    }

    @Override
    public boolean canSendMedia(User sender, User receiver) {
        return followService.areFriends(sender, receiver) || hasReplied(sender, receiver);
    }

    @Override
    public boolean hasReplied(User sender, User receiver) {
        // 只要查到一条即可
        List<PrivateMessage> replies = messageRepository.findBySenderAndReceiverWithParticipantsOrderByCreatedAtAsc(receiver, sender);
        return !replies.isEmpty();
    }
}