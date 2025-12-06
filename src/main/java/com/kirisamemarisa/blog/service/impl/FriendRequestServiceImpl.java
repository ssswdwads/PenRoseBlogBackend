package com.kirisamemarisa.blog.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kirisamemarisa.blog.model.FriendRequest;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.repository.FriendRequestRepository;
import com.kirisamemarisa.blog.service.FriendRequestService;
import com.kirisamemarisa.blog.service.FollowService;
import com.kirisamemarisa.blog.service.NotificationService;
import com.kirisamemarisa.blog.dto.FriendRequestDTO;
import com.kirisamemarisa.blog.dto.NotificationDTO;
import com.kirisamemarisa.blog.mapper.FriendRequestMapper;
import com.kirisamemarisa.blog.repository.UserProfileRepository;
import com.kirisamemarisa.blog.model.UserProfile;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@Transactional
public class FriendRequestServiceImpl implements FriendRequestService {
    private static final Logger logger = LoggerFactory.getLogger(FriendRequestServiceImpl.class);

    private final FriendRequestRepository friendRequestRepository;
    private final NotificationService notificationService;
    private final FollowService followService;
    private final UserProfileRepository userProfileRepository;

    public FriendRequestServiceImpl(FriendRequestRepository friendRequestRepository,
            NotificationService notificationService,
            FollowService followService,
            UserProfileRepository userProfileRepository) {
        this.friendRequestRepository = friendRequestRepository;
        this.notificationService = notificationService;
        this.followService = followService;
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public FriendRequestDTO sendRequest(User sender, User receiver, String message) {
        friendRequestRepository.findBySenderAndReceiverAndStatus(sender, receiver, FriendRequest.Status.PENDING)
                .ifPresent(req -> {
                    throw new IllegalStateException("已有未处理的好友申请");
                });

        FriendRequest req = new FriendRequest();
        req.setSender(sender);
        req.setReceiver(receiver);
        req.setMessage(message);
        req.setStatus(FriendRequest.Status.PENDING);
        FriendRequest saved = friendRequestRepository.save(req);
        boolean online = notificationService.isOnline(receiver.getId());
        NotificationDTO note = FriendRequestMapper.toNotification(saved);
        note.setType("FRIEND_REQUEST");
        // senderUsername 可提前补充
        if (saved.getSender() != null) {
            note.setSenderUsername(saved.getSender().getUsername());
        }

        // send notification after the transaction commits to avoid inconsistency
        sendNotificationAfterCommit(receiver.getId(), note, saved.getSender().getId(), "friend request");

        logger.info("Sent friend request {} from {} to {} (online={})", saved.getId(), sender.getId(), receiver.getId(),
                online);
        FriendRequestDTO dto = FriendRequestMapper.toDTO(saved);
        // populate sender profile info
        if (dto.getSenderId() != null) {
            UserProfile up = userProfileRepository.findById(dto.getSenderId()).orElse(null);
            if (up != null) {
                dto.setSenderNickname(up.getNickname());
                dto.setSenderAvatarUrl(up.getAvatarUrl());
            }
        }
        return dto;
    }

    @Override
    public List<FriendRequestDTO> pendingFor(User receiver) {
        List<FriendRequest> list = friendRequestRepository.findByReceiverAndStatus(receiver,
                FriendRequest.Status.PENDING);
        List<FriendRequestDTO> dtos = FriendRequestMapper.toDTOList(list);
        // batch load sender profiles
        java.util.Set<Long> senderIds = new java.util.HashSet<>();
        for (FriendRequestDTO d : dtos)
            if (d.getSenderId() != null)
                senderIds.add(d.getSenderId());
        if (!senderIds.isEmpty()) {
            java.util.List<UserProfile> profiles = userProfileRepository.findAllById(senderIds);
            java.util.Map<Long, UserProfile> profileMap = new java.util.HashMap<>();
            for (UserProfile p : profiles)
                profileMap.put(p.getId(), p);
            for (FriendRequestDTO d : dtos) {
                UserProfile p = profileMap.get(d.getSenderId());
                if (p != null) {
                    d.setSenderNickname(p.getNickname());
                    d.setSenderAvatarUrl(p.getAvatarUrl());
                }
            }
        }
        return dtos;
    }

    @Override
    public FriendRequestDTO respond(Long requestId, User receiver, boolean accept) {
        FriendRequest req = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("好友申请不存在"));
        if (!req.getReceiver().getId().equals(receiver.getId()))
            throw new SecurityException("无权处理此申请");
        if (req.getStatus() != FriendRequest.Status.PENDING)
            throw new IllegalStateException("申请已处理");
        if (accept) {
            req.setStatus(FriendRequest.Status.ACCEPTED);
            // create mutual follow relationship
            try {
                followService.follow(req.getSender(), req.getReceiver());
            } catch (Exception ex) {
                logger.error("Failed to follow sender->receiver when accepting friend request {}: {}", req.getId(),
                        ex.toString());
                throw new RuntimeException("好友互相关注失败(sender->receiver): " + ex.getMessage(), ex);
            }
            try {
                followService.follow(req.getReceiver(), req.getSender());
            } catch (Exception ex) {
                logger.error("Failed to follow receiver->sender when accepting friend request {}: {}", req.getId(),
                        ex.toString());
                throw new RuntimeException("好友互相关注失败(receiver->sender): " + ex.getMessage(), ex);
            }
        } else {
            req.setStatus(FriendRequest.Status.REJECTED);
        }
        FriendRequest saved = friendRequestRepository.save(req);
        NotificationDTO note = FriendRequestMapper.toNotification(saved);
        note.setType("FRIEND_REQUEST_RESPONSE");
        if (saved.getSender() != null) {
            note.setSenderUsername(saved.getSender().getUsername());
        }

        // ensure notification sending is done after transaction commit
        sendNotificationAfterCommit(req.getSender().getId(), note, saved.getSender().getId(), "friend request response");

        FriendRequestDTO dto = FriendRequestMapper.toDTO(saved);
        if (dto.getSenderId() != null) {
            UserProfile up = userProfileRepository.findById(dto.getSenderId()).orElse(null);
            if (up != null) {
                dto.setSenderNickname(up.getNickname());
                dto.setSenderAvatarUrl(up.getAvatarUrl());
            }
        }
        return dto;
    }

    /**
     * 事务提交后查 profile 并补充到 NotificationDTO，再推送
     */
    private void sendNotificationAfterCommit(Long userId, NotificationDTO note, Long senderIdForProfile, String what) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        UserProfile up = userProfileRepository.findById(senderIdForProfile).orElse(null);
                        if (up != null) {
                            note.setSenderNickname(up.getNickname());
                            note.setSenderAvatarUrl(up.getAvatarUrl());
                        }
                        notificationService.sendNotification(userId, note);
                    } catch (Exception ex) {
                        logger.error("Failed to send {} notification for sender {}: {}", what, senderIdForProfile, ex.toString());
                    }
                }
            });
        } else {
            try {
                UserProfile up = userProfileRepository.findById(senderIdForProfile).orElse(null);
                if (up != null) {
                    note.setSenderNickname(up.getNickname());
                    note.setSenderAvatarUrl(up.getAvatarUrl());
                }
                notificationService.sendNotification(userId, note);
            } catch (Exception ex) {
                logger.error("Failed to send {} notification for sender {}: {}", what, senderIdForProfile, ex.toString());
            }
        }
    }
}
