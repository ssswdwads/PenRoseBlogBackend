package com.kirisamemarisa.blog.controller;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.PageResult;
import com.kirisamemarisa.blog.dto.PrivateMessageDTO;
import com.kirisamemarisa.blog.events.MessageEventPublisher;
import com.kirisamemarisa.blog.model.PrivateMessage;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.model.UserProfile;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.service.PrivateMessageService;
import com.kirisamemarisa.blog.repository.PrivateMessageRepository;
import com.kirisamemarisa.blog.dto.ConversationSummaryDTO;
import com.kirisamemarisa.blog.repository.UserProfileRepository;
import com.kirisamemarisa.blog.service.NotificationService;
import com.kirisamemarisa.blog.dto.NotificationDTO;
import com.kirisamemarisa.blog.service.BlogUrlPreviewService;

import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/messages")
public class PrivateMessageController {
    private final UserRepository userRepository;
    private final PrivateMessageService privateMessageService;
    private final PrivateMessageRepository privateMessageRepository;
    private final MessageEventPublisher publisher;
    private final UserProfileRepository userProfileRepository;
    private final NotificationService notificationService;
    private final BlogUrlPreviewService blogUrlPreviewService;

    @Value("${resource.message-media-location:uploads/messages}")
    private String messageMediaLocation;

    @Value("${resource.message-media-access-prefix:/files/messages}")
    private String messageMediaAccessPrefix;

    public PrivateMessageController(UserRepository userRepository,
                                    PrivateMessageService privateMessageService,
                                    PrivateMessageRepository privateMessageRepository,
                                    MessageEventPublisher publisher,
                                    UserProfileRepository userProfileRepository,
                                    NotificationService notificationService,
                                    BlogUrlPreviewService blogUrlPreviewService) {
        this.userRepository = userRepository;
        this.privateMessageService = privateMessageService;
        this.privateMessageRepository = privateMessageRepository;
        this.publisher = publisher;
        this.userProfileRepository = userProfileRepository;
        this.notificationService = notificationService;
        this.blogUrlPreviewService = blogUrlPreviewService;
    }

    private User resolveCurrent(UserDetails principal, Long headerUserId) {
        if (principal != null)
            return userRepository.findByUsername(principal.getUsername());
        if (headerUserId != null)
            return userRepository.findById(headerUserId).orElse(null);
        return null;
    }

    // 优化后的转换方法，支持传入预查询好的 Profile Map
    private PrivateMessageDTO toDTO(PrivateMessage msg, Map<Long, UserProfile> profileMap) {
        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setId(msg.getId());
        dto.setSenderId(msg.getSender().getId());
        dto.setReceiverId(msg.getReceiver().getId());
        dto.setText(msg.getText());
        dto.setMediaUrl(msg.getMediaUrl());
        dto.setType(msg.getType());
        dto.setCreatedAt(msg.getCreatedAt());

        Long sid = msg.getSender() != null ? msg.getSender().getId() : null;
        Long rid = msg.getReceiver() != null ? msg.getReceiver().getId() : null;

        if (sid != null) {
            UserProfile sp = profileMap != null ? profileMap.get(sid) : null;
            if (sp != null) {
                dto.setSenderNickname(sp.getNickname());
                dto.setSenderAvatarUrl(sp.getAvatarUrl());
            } else {
                dto.setSenderNickname(msg.getSender() != null ? msg.getSender().getUsername() : "");
                dto.setSenderAvatarUrl("");
            }
        }
        if (rid != null) {
            UserProfile rp = profileMap != null ? profileMap.get(rid) : null;
            if (rp != null) {
                dto.setReceiverNickname(rp.getNickname());
                dto.setReceiverAvatarUrl(rp.getAvatarUrl());
            } else {
                dto.setReceiverNickname(msg.getReceiver() != null ? msg.getReceiver().getUsername() : "");
                dto.setReceiverAvatarUrl("");
            }
        }

        // 如果包含站内博客链接，生成预览
        dto.setBlogPreview(blogUrlPreviewService.extractPreviewFromText(dto.getText()));

        return dto;
    }

    // 辅助方法：单条消息转换（会单独查询 Profile，仅用于发送消息返回值）
    private PrivateMessageDTO toDTOSingle(PrivateMessage msg) {
        Map<Long, UserProfile> map = new HashMap<>();
        if (msg.getSender() != null)
            userProfileRepository.findById(msg.getSender().getId()).ifPresent(p -> map.put(p.getId(), p));
        if (msg.getReceiver() != null)
            userProfileRepository.findById(msg.getReceiver().getId()).ifPresent(p -> map.put(p.getId(), p));
        return toDTO(msg, map);
    }

    /**
     * SSE 订阅接口
     * 前端：/api/messages/subscribe/{otherId}?userId={当前用户ID}&_={timestamp}
     */
    @GetMapping(value = "/subscribe/{otherId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeConversation(@PathVariable Long otherId,
                                            @RequestParam("userId") Long userId,
                                            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
                                            @AuthenticationPrincipal UserDetails principal) {
        // 简单鉴权：userId 必须等于当前登录的用户
        User me = resolveCurrent(principal, headerUserId);
        if (me == null || !Objects.equals(me.getId(), userId)) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }

        User other = userRepository.findById(otherId).orElse(null);
        if (other == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }

        Pageable pageable = PageRequest.of(0, 20);
        Page<PrivateMessage> pmPage = privateMessageService.conversationPage(me, other, pageable);

        Set<Long> userIds = new HashSet<>();
        pmPage.getContent().forEach(m -> {
            if (m.getSender() != null) userIds.add(m.getSender().getId());
            if (m.getReceiver() != null) userIds.add(m.getReceiver().getId());
        });

        Map<Long, UserProfile> profileMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<UserProfile> profiles = userProfileRepository.findAllById(userIds);
            profiles.forEach(p -> profileMap.put(p.getId(), p));
        }

        List<PrivateMessageDTO> dtoList = pmPage.getContent().stream()
                .map(msg -> toDTO(msg, profileMap))
                .collect(Collectors.toList());
        Collections.reverse(dtoList);

        return publisher.subscribe(me.getId(), other.getId(), dtoList);
    }

    @GetMapping("/conversation/{otherId}")
    public ApiResponse<PageResult<PrivateMessageDTO>> conversation(@PathVariable Long otherId,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size,
                                                                   @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
                                                                   @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null) return new ApiResponse<>(401, "未认证", null);
        User other = userRepository.findById(otherId).orElse(null);
        if (other == null) return new ApiResponse<>(404, "用户不存在", null);

        Pageable pageable = PageRequest.of(page, size);
        Page<PrivateMessage> pmPage = privateMessageService.conversationPage(me, other, pageable);

        Set<Long> userIds = new HashSet<>();
        pmPage.getContent().forEach(m -> {
            if (m.getSender() != null) userIds.add(m.getSender().getId());
            if (m.getReceiver() != null) userIds.add(m.getReceiver().getId());
        });

        Map<Long, UserProfile> profileMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<UserProfile> profiles = userProfileRepository.findAllById(userIds);
            profiles.forEach(p -> profileMap.put(p.getId(), p));
        }

        List<PrivateMessageDTO> dtoList = pmPage.getContent().stream()
                .map(msg -> toDTO(msg, profileMap))
                .collect(Collectors.toList());

        Collections.reverse(dtoList);

        return new ApiResponse<>(200, "OK", new PageResult<>(dtoList, pmPage.getTotalElements(), page, size));
    }

    @GetMapping("/conversation/list")
    public ApiResponse<PageResult<ConversationSummaryDTO>> listConversations(
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null) return new ApiResponse<>(401, "未认证", null);

        Map<Long, ConversationSummaryDTO> map = new LinkedHashMap<>();

        privateMessageRepository.findBySenderWithReceiverOrderByCreatedAtDesc(me).forEach(m -> {
            User receiver = m.getReceiver();
            Long otherId = receiver != null ? receiver.getId() : null;
            if (otherId == null) return;
            ConversationSummaryDTO cur = map.get(otherId);
            if (cur == null || m.getCreatedAt().isAfter(cur.getLastAt())) {
                ConversationSummaryDTO s = new ConversationSummaryDTO();
                s.setOtherId(otherId);
                UserProfile prof = userProfileRepository.findById(otherId).orElse(null);
                if (prof != null) { s.setNickname(prof.getNickname()); s.setAvatarUrl(prof.getAvatarUrl()); }
                else { s.setNickname(receiver.getUsername()); s.setAvatarUrl(""); }
                s.setLastMessage(choosePreview(m));
                s.setLastAt(m.getCreatedAt());
                map.put(otherId, s);
            }
        });

        privateMessageRepository.findByReceiverWithSenderOrderByCreatedAtDesc(me).forEach(m -> {
            User sender = m.getSender();
            Long otherId = sender != null ? sender.getId() : null;
            if (otherId == null) return;
            ConversationSummaryDTO cur = map.get(otherId);
            if (cur == null || m.getCreatedAt().isAfter(cur.getLastAt())) {
                ConversationSummaryDTO s = new ConversationSummaryDTO();
                UserProfile prof2 = userProfileRepository.findById(otherId).orElse(null);
                s.setOtherId(otherId);
                if (prof2 != null) { s.setNickname(prof2.getNickname()); s.setAvatarUrl(prof2.getAvatarUrl()); }
                else { s.setNickname(sender.getUsername()); s.setAvatarUrl(""); }
                s.setLastMessage(choosePreview(m));
                s.setLastAt(m.getCreatedAt());
                map.put(otherId, s);
            }
        });

        List<ConversationSummaryDTO> list = new ArrayList<>(map.values());
        list.forEach(s -> {
            long unread = privateMessageRepository.countUnreadBetween(me.getId(), s.getOtherId());
            s.setUnreadCount(unread);
        });
        Collections.sort(list, Comparator.comparing(ConversationSummaryDTO::getLastAt).reversed());
        PageResult<ConversationSummaryDTO> page = new PageResult<>(list, list.size(), 0, list.size());
        return new ApiResponse<>(200, "OK", page);
    }

    @GetMapping("/unread/total")
    public ApiResponse<Long> unreadTotal(
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null) return new ApiResponse<>(401, "未认证", null);
        long total = privateMessageRepository.countUnreadTotal(me.getId());
        return new ApiResponse<>(200, "OK", total);
    }

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
            String base = m.getType().name();
            String t = m.getText();
            if (t != null && !t.isEmpty()) {
                String cut = t.length() > 20 ? t.substring(0, 20) + "..." : t;
                return base + ":" + cut;
            }
            return base;
        }
        String t = m.getText();
        if (t == null) return "";
        return t.length() > 40 ? t.substring(0, 40) + "..." : t;
    }

    private void sendPmNotification(PrivateMessage msg) {
        try {
            if (notificationService == null) return;
            NotificationDTO dto = new NotificationDTO();
            dto.setType("PRIVATE_MESSAGE");
            dto.setSenderId(msg.getSender() != null ? msg.getSender().getId() : null);
            dto.setReceiverId(msg.getReceiver() != null ? msg.getReceiver().getId() : null);
            dto.setMessage(choosePreview(msg));
            dto.setStatus(null);
            dto.setCreatedAt(Instant.now());
            dto.setReferenceId(msg.getId()); // 业务主键：私信ID

            Long receiverId = dto.getReceiverId();
            if (receiverId != null && !receiverId.equals(dto.getSenderId())) {
                notificationService.sendNotification(receiverId, dto);
            }
        } catch (Exception ignored) { }
    }

    @PostMapping("/text/{otherId}")
    public ApiResponse<PrivateMessageDTO> sendText(@PathVariable Long otherId,
                                                   @RequestBody PrivateMessageDTO body,
                                                   @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
                                                   @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null) return new ApiResponse<>(401, "未认证", null);
        User other = userRepository.findById(otherId).orElse(null);
        if (other == null) return new ApiResponse<>(404, "用户不存在", null);

        try {
            PrivateMessage msg = privateMessageService.sendText(me, other, body.getText());
            PrivateMessageDTO dto = toDTOSingle(msg);

            // 系统级通知
            sendPmNotification(msg);

            // 最新一页，用于 SSE 广播
            Pageable pageable = PageRequest.of(0, 20);
            Page<PrivateMessage> pmPage = privateMessageService.conversationPage(me, other, pageable);

            Set<Long> userIds = new HashSet<>();
            pmPage.getContent().forEach(m -> {
                if (m.getSender() != null) userIds.add(m.getSender().getId());
                if (m.getReceiver() != null) userIds.add(m.getReceiver().getId());
            });
            Map<Long, UserProfile> profileMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<UserProfile> profiles = userProfileRepository.findAllById(userIds);
                profiles.forEach(p -> profileMap.put(p.getId(), p));
            }
            List<PrivateMessageDTO> dtoList = pmPage.getContent().stream()
                    .map(m -> toDTO(m, profileMap))
                    .collect(Collectors.toList());
            Collections.reverse(dtoList);

            publisher.broadcast(me.getId(), other.getId(), dtoList);

            return new ApiResponse<>(200, "发送成功", dto);
        } catch (IllegalStateException ex) {
            return new ApiResponse<>(400, ex.getMessage(), null);
        }
    }

    @PostMapping("/media/{otherId}")
    public ApiResponse<PrivateMessageDTO> sendMedia(@PathVariable Long otherId,
                                                    @RequestBody PrivateMessageDTO body,
                                                    @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
                                                    @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null) return new ApiResponse<>(401, "未认证", null);
        User other = userRepository.findById(otherId).orElse(null);
        if (other == null) return new ApiResponse<>(404, "用户不存在", null);

        try {
            PrivateMessage msg = privateMessageService.sendMedia(me, other, body.getType(), body.getMediaUrl(), body.getText());
            PrivateMessageDTO dto = toDTOSingle(msg);

            sendPmNotification(msg);

            Pageable pageable = PageRequest.of(0, 20);
            Page<PrivateMessage> pmPage = privateMessageService.conversationPage(me, other, pageable);

            Set<Long> userIds = new HashSet<>();
            pmPage.getContent().forEach(m -> {
                if (m.getSender() != null) userIds.add(m.getSender().getId());
                if (m.getReceiver() != null) userIds.add(m.getReceiver().getId());
            });
            Map<Long, UserProfile> profileMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<UserProfile> profiles = userProfileRepository.findAllById(userIds);
                profiles.forEach(p -> profileMap.put(p.getId(), p));
            }
            List<PrivateMessageDTO> dtoList = pmPage.getContent().stream()
                    .map(m -> toDTO(m, profileMap))
                    .collect(Collectors.toList());
            Collections.reverse(dtoList);

            publisher.broadcast(me.getId(), other.getId(), dtoList);

            return new ApiResponse<>(200, "发送成功", dto);
        } catch (IllegalStateException ex) {
            return new ApiResponse<>(400, ex.getMessage(), null);
        }
    }

    private String saveMessageMediaFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
                ext = originalFilename.substring(dotIndex);
            }
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        java.nio.file.Path dirPath = java.nio.file.Paths.get(messageMediaLocation).toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new IOException("创建上传目录失败: " + dirPath, e);
        }

        java.nio.file.Path destPath = dirPath.resolve(filename).normalize();
        try {
            file.transferTo(destPath.toFile());
        } catch (IOException e) {
            throw new IOException("保存上传文件失败: " + destPath, e);
        }

        String prefix = messageMediaAccessPrefix;
        if (prefix == null || prefix.isEmpty()) prefix = "/files/messages";
        if (!prefix.startsWith("/")) prefix = "/" + prefix;
        if (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);

        return prefix + "/" + filename;
    }

    @PostMapping("/upload")
    public ApiResponse<String> uploadMessageMedia(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {

        User me = resolveCurrent(principal, headerUserId);
        if (me == null) return new ApiResponse<>(401, "未认证", null);
        if (file == null || file.isEmpty()) return new ApiResponse<>(400, "上传文件不能为空", null);

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            return new ApiResponse<>(400, "仅支持图片或视频文件", null);
        }

        try {
            String url = saveMessageMediaFile(file);
            return new ApiResponse<>(200, "上传成功", url);
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(400, e.getMessage(), null);
        } catch (IOException e) {
            return new ApiResponse<>(500, "服务器保存文件失败", null);
        }
    }
}