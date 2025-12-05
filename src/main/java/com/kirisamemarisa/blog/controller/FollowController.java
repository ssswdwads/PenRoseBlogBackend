package com.kirisamemarisa.blog.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.UserSimpleDTO;
import com.kirisamemarisa.blog.dto.PageResult;
import com.kirisamemarisa.blog.mapper.UserSimpleMapper;
import com.kirisamemarisa.blog.repository.UserProfileRepository;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.model.UserProfile;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.service.FollowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 关注相关接口：兼容未登录测试（通过 X-User-Id 头传当前用户ID）。
 */
@RestController
@RequestMapping("/api/follow")
public class FollowController {
    private static final Logger logger = LoggerFactory.getLogger(FollowController.class);

    private final UserRepository userRepository;
    private final FollowService followService;
    private final UserProfileRepository userProfileRepository;

    public FollowController(UserRepository userRepository, FollowService followService,
            UserProfileRepository userProfileRepository) {
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

    @PostMapping("/{targetId}")
    public ApiResponse<Void> follow(@PathVariable Long targetId,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrentUser(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        User target = userRepository.findById(targetId).orElse(null);
        if (target == null) {
            return new ApiResponse<>(404, "目标用户不存在", null);
        }
        if (me.getId().equals(targetId)) {
            return new ApiResponse<>(400, "不能关注自己", null);
        }
        if (followService.isFollowing(me, target)) {
            return new ApiResponse<>(200, "已关注，无需重复操作", null);
        }
        followService.follow(me, target);
        return new ApiResponse<>(200, "关注成功", null);
    }

    @DeleteMapping("/{targetId}")
    public ApiResponse<Void> unfollow(@PathVariable Long targetId,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrentUser(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        User target = userRepository.findById(targetId).orElse(null);
        if (target == null) {
            return new ApiResponse<>(404, "目标用户不存在", null);
        }
        if (!followService.isFollowing(me, target)) {
            return new ApiResponse<>(200, "未关注，无需取关", null);
        }
        followService.unfollow(me, target);
        return new ApiResponse<>(200, "取关成功", null);
    }

    @GetMapping("/followers")
    public ApiResponse<PageResult<UserSimpleDTO>> followers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrentUser(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        List<Object[]> list = followService.pageFollowers(me, PageRequest.of(page, size));
        long total = followService.countFollowers(me);
        List<UserSimpleDTO> dtoList = list.stream()
                .map(arr -> UserSimpleMapper.INSTANCE.toDTO((User) arr[0], (UserProfile) arr[1]))
                .toList();
        return new ApiResponse<>(200, "获取成功", new PageResult<>(dtoList, total, page, size));
    }

    @GetMapping("/following")
    public ApiResponse<PageResult<UserSimpleDTO>> following(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrentUser(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        List<Object[]> list = followService.pageFollowing(me, PageRequest.of(page, size));
        long total = followService.countFollowing(me);
        List<UserSimpleDTO> dtoList = list.stream()
                .map(arr -> UserSimpleMapper.INSTANCE.toDTO((User) arr[0], (UserProfile) arr[1]))
                .toList();
        return new ApiResponse<>(200, "获取成功", new PageResult<>(dtoList, total, page, size));
    }

    @GetMapping("/friends/{otherId}")
    public ApiResponse<Boolean> isFriends(@PathVariable Long otherId,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrentUser(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        User other = userRepository.findById(otherId).orElse(null);
        if (other == null) {
            return new ApiResponse<>(404, "用户不存在", null);
        }
        boolean friends = followService.areFriends(me, other);
        return new ApiResponse<>(200, "OK", friends);
    }

    @GetMapping("/friends/list")
    public ApiResponse<List<UserSimpleDTO>> friendsList(
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrentUser(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        List<User> followers = followService.listFollowers(me);
        List<User> following = followService.listFollowing(me);
        // compute mutuals by id
        java.util.Set<Long> followingIds = following.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
        List<UserSimpleDTO> dtoList = followers.stream()
                .filter(u -> followingIds.contains(u.getId()))
                .map(u -> {
                    com.kirisamemarisa.blog.model.UserProfile profile = userProfileRepository.findById(u.getId()).orElse(null);
                    return UserSimpleMapper.INSTANCE.toDTO(u, profile);
                })
                .toList();
        return new ApiResponse<>(200, "获取成功", dtoList);
    }
}
