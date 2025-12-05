package com.kirisamemarisa.blog.controller;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.UserSimpleDTO;
import com.kirisamemarisa.blog.mapper.UserSimpleMapper;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.model.UserProfile;
import com.kirisamemarisa.blog.repository.UserProfileRepository;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.service.FollowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SocialController {
    private static final Logger logger = LoggerFactory.getLogger(SocialController.class);

    private final UserRepository userRepository;
    private final FollowService followService;
    private final UserProfileRepository userProfileRepository;

    public SocialController(UserRepository userRepository, FollowService followService, UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.followService = followService;
        this.userProfileRepository = userProfileRepository;
    }

    private User resolveCurrentUser(UserDetails principal, Long headerUserId) {
        if (principal != null) {
            return userRepository.findByUsername(principal.getUsername());
        }
        if (headerUserId != null) {
            return userRepository.findById(headerUserId).orElse(null);
        }
        return null;
    }

    @GetMapping("/friends/list")
    public ApiResponse<List<UserSimpleDTO>> friendsList(@RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
                                                         @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrentUser(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        List<User> followers = followService.listFollowers(me);
        List<User> following = followService.listFollowing(me);
        Set<Long> followingIds = following.stream().map(User::getId).collect(Collectors.toSet());
        List<UserSimpleDTO> dtoList = followers.stream()
                .filter(u -> followingIds.contains(u.getId()))
                .map(u -> {
                    UserProfile profile = userProfileRepository.findById(u.getId()).orElse(null);
                    return UserSimpleMapper.INSTANCE.toDTO(u, profile);
                })
                .collect(Collectors.toList());
        return new ApiResponse<>(200, "获取成功", dtoList);
    }

    @GetMapping("/following")
    public ApiResponse<List<UserSimpleDTO>> followingList(@RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
                                                           @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrentUser(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        List<User> following = followService.listFollowing(me);
        List<UserSimpleDTO> dtoList = following.stream()
                .map(u -> {
                    UserProfile profile = userProfileRepository.findById(u.getId()).orElse(null);
                    return UserSimpleMapper.INSTANCE.toDTO(u, profile);
                })
                .collect(Collectors.toList());
        return new ApiResponse<>(200, "获取成功", dtoList);
    }

    @GetMapping("/followers")
    public ApiResponse<List<UserSimpleDTO>> followersList(@RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
                                                           @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrentUser(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        List<User> followers = followService.listFollowers(me);
        List<UserSimpleDTO> dtoList = followers.stream()
                .map(u -> {
                    UserProfile profile = userProfileRepository.findById(u.getId()).orElse(null);
                    return UserSimpleMapper.INSTANCE.toDTO(u, profile);
                })
                .collect(Collectors.toList());
        return new ApiResponse<>(200, "获取成功", dtoList);
    }
}
