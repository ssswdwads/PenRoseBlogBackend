package com.kirisamemarisa.blog.controller;

// logger not needed here
import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.PageResult;
import com.kirisamemarisa.blog.dto.PrivateMessageDTO;
import com.kirisamemarisa.blog.events.MessageEventPublisher;
import com.kirisamemarisa.blog.model.PrivateMessage;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.service.PrivateMessageService;
import com.kirisamemarisa.blog.repository.PrivateMessageRepository;
import com.kirisamemarisa.blog.dto.ConversationSummaryDTO;
import com.kirisamemarisa.blog.model.UserProfile;
import com.kirisamemarisa.blog.repository.UserProfileRepository;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Collections;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * 私信发送与会话查询（增加未认证头兼容 + 推送事件）。
 */
@RestController
@RequestMapping("/api/messages")
public class PrivateMessageController {
    // controller for private message endpoints
    private final UserRepository userRepository;
    private final PrivateMessageService privateMessageService;
    private final PrivateMessageRepository privateMessageRepository;
    private final MessageEventPublisher publisher;
    private final UserProfileRepository userProfileRepository;

    public PrivateMessageController(UserRepository userRepository,
            PrivateMessageService privateMessageService,
            PrivateMessageRepository privateMessageRepository,
            MessageEventPublisher publisher,
            UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.privateMessageService = privateMessageService;
        this.privateMessageRepository = privateMessageRepository;
        this.publisher = publisher;
        this.userProfileRepository = userProfileRepository;
    }

    private User resolveCurrent(UserDetails principal, Long headerUserId) {
        if (principal != null)
            return userRepository.findByUsername(principal.getUsername());
        if (headerUserId != null)
            return userRepository.findById(headerUserId).orElse(null);
        return null;
    }

    private PrivateMessageDTO toDTO(PrivateMessage msg) {
        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setId(msg.getId());
        dto.setSenderId(msg.getSender().getId());
        dto.setReceiverId(msg.getReceiver().getId());
        dto.setText(msg.getText());
        dto.setMediaUrl(msg.getMediaUrl());
        dto.setType(msg.getType());
        dto.setCreatedAt(msg.getCreatedAt());
        // populate nicknames and avatar urls when available
        Long sid = msg.getSender() != null ? msg.getSender().getId() : null;
        Long rid = msg.getReceiver() != null ? msg.getReceiver().getId() : null;
        if (sid != null) {
            UserProfile sp = userProfileRepository.findById(sid).orElse(null);
            if (sp != null) {
                dto.setSenderNickname(sp.getNickname());
                dto.setSenderAvatarUrl(sp.getAvatarUrl());
            } else {
                dto.setSenderNickname(msg.getSender() != null ? msg.getSender().getUsername() : "");
                dto.setSenderAvatarUrl("");
            }
        }
        if (rid != null) {
            UserProfile rp = userProfileRepository.findById(rid).orElse(null);
            if (rp != null) {
                dto.setReceiverNickname(rp.getNickname());
                dto.setReceiverAvatarUrl(rp.getAvatarUrl());
            } else {
                dto.setReceiverNickname(msg.getReceiver() != null ? msg.getReceiver().getUsername() : "");
                dto.setReceiverAvatarUrl("");
            }
        }
        return dto;
    }

    private ApiResponse<PageResult<PrivateMessageDTO>> buildConversation(User me, User other, int page, int size) {
        List<PrivateMessageDTO> all = privateMessageService
                .conversation(me, other)
                .stream().map(this::toDTO).toList();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<PrivateMessageDTO> pageList = all.subList(from, to);
        return new ApiResponse<>(200, "OK", new PageResult<>(pageList, all.size(), page, size));
    }

    @GetMapping("/conversation/{otherId}")
    public ApiResponse<PageResult<PrivateMessageDTO>> conversation(@PathVariable Long otherId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null)
            return new ApiResponse<>(401, "未认证", null);
        User other = userRepository.findById(otherId).orElse(null);
        if (other == null)
            return new ApiResponse<>(404, "用户不存在", null);
        return buildConversation(me, other, page, size);
    }

    @GetMapping("/conversation/list")
    public ApiResponse<PageResult<ConversationSummaryDTO>> listConversations(
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null)
            return new ApiResponse<>(401, "未认证", null);

        Map<Long, ConversationSummaryDTO> map = new LinkedHashMap<>();

        // 发出的消息（对方 = 接收者）
        privateMessageRepository.findBySenderWithReceiverOrderByCreatedAtDesc(me).forEach(m -> {
            User receiver = m.getReceiver();
            Long otherId = receiver != null ? receiver.getId() : null;
            if (otherId == null) return;
            ConversationSummaryDTO cur = map.get(otherId);
            if (cur == null || m.getCreatedAt().isAfter(cur.getLastAt())) {
                ConversationSummaryDTO s = new ConversationSummaryDTO();
                s.setOtherId(otherId);
                com.kirisamemarisa.blog.model.UserProfile prof = userProfileRepository.findById(otherId).orElse(null);
                if (prof != null) { s.setNickname(prof.getNickname()); s.setAvatarUrl(prof.getAvatarUrl()); }
                else { s.setNickname(receiver.getUsername()); s.setAvatarUrl(""); }
                s.setLastMessage(choosePreview(m));
                s.setLastAt(m.getCreatedAt());
                map.put(otherId, s);
            }
        });

        // 收到的消息（对方 = 发送者）
        privateMessageRepository.findByReceiverWithSenderOrderByCreatedAtDesc(me).forEach(m -> {
            User sender = m.getSender();
            Long otherId = sender != null ? sender.getId() : null;
            if (otherId == null) return;
            ConversationSummaryDTO cur = map.get(otherId);
            if (cur == null || m.getCreatedAt().isAfter(cur.getLastAt())) {
                ConversationSummaryDTO s = new ConversationSummaryDTO();
                com.kirisamemarisa.blog.model.UserProfile prof2 = userProfileRepository.findById(otherId).orElse(null);
                s.setOtherId(otherId);
                if (prof2 != null) { s.setNickname(prof2.getNickname()); s.setAvatarUrl(prof2.getAvatarUrl()); }
                else { s.setNickname(sender.getUsername()); s.setAvatarUrl(""); }
                s.setLastMessage(choosePreview(m));
                s.setLastAt(m.getCreatedAt());
                map.put(otherId, s);
            }
        });

        java.util.List<ConversationSummaryDTO> list = new java.util.ArrayList<>(map.values());
        // 计算每个会话未读数
        list.forEach(s -> {
            long unread = privateMessageRepository.countUnreadBetween(me.getId(), s.getOtherId());
            s.setUnreadCount(unread);
        });
        Collections.sort(list, Comparator.comparing(ConversationSummaryDTO::getLastAt).reversed());
        PageResult<ConversationSummaryDTO> page = new PageResult<>(list, list.size(), 0, list.size());
        return new ApiResponse<>(200, "OK", page);
    }

    // 未读总数
    @GetMapping("/unread/total")
    public ApiResponse<Long> unreadTotal(
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null) return new ApiResponse<>(401, "未认证", null);
        long total = privateMessageRepository.countUnreadTotal(me.getId());
        return new ApiResponse<>(200, "OK", total);
    }

    // 将与某人的会话标记为已读
    @PostMapping("/conversation/{otherId}/read")
    @Transactional
    public ApiResponse<Integer> markRead(@PathVariable Long otherId,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null) return new ApiResponse<>(401, "未认证", null);
        int updated = privateMessageRepository.markConversationRead(otherId, me.getId());
        return new ApiResponse<>(200, "OK", updated);
    }

    private String choosePreview(PrivateMessage m) {
        if (m.getType() != null && m.getType() != PrivateMessage.MessageType.TEXT) {
            return m.getType().name();
        }
        String t = m.getText();
        if (t == null)
            return "";
        return t.length() > 40 ? t.substring(0, 40) + "..." : t;
    }

    @PostMapping("/text/{otherId}")
    public ApiResponse<PrivateMessageDTO> sendText(@PathVariable Long otherId,
            @RequestBody PrivateMessageDTO body,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null)
            return new ApiResponse<>(401, "未认证", null);
        User other = userRepository.findById(otherId).orElse(null);
        if (other == null)
            return new ApiResponse<>(404, "用户不存在", null);
        PrivateMessage msg = privateMessageService.sendText(me, other, body.getText());
        PrivateMessageDTO dto = toDTO(msg);
        // 推送最新会话
        publisher.broadcast(me.getId(), other.getId(),
                privateMessageService.conversation(me, other).stream().map(this::toDTO).collect(Collectors.toList()));
        return new ApiResponse<>(200, "发送成功", dto);
    }

    @PostMapping("/media/{otherId}")
    public ApiResponse<PrivateMessageDTO> sendMedia(@PathVariable Long otherId,
            @RequestBody PrivateMessageDTO body,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null)
            return new ApiResponse<>(401, "未认证", null);
        User other = userRepository.findById(otherId).orElse(null);
        if (other == null)
            return new ApiResponse<>(404, "用户不存在", null);
        PrivateMessage msg = privateMessageService.sendMedia(me, other, body.getType(), body.getMediaUrl(),
                body.getText());
        PrivateMessageDTO dto = toDTO(msg);
        publisher.broadcast(me.getId(), other.getId(),
                privateMessageService.conversation(me, other).stream().map(this::toDTO).collect(Collectors.toList()));
        return new ApiResponse<>(200, "发送成功", dto);
    }
}
