package com.kirisamemarisa.blog.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kirisamemarisa.blog.model.Follow;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.repository.FollowRepository;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.service.FollowService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class FollowServiceImpl implements FollowService {
    private static final Logger logger = LoggerFactory.getLogger(FollowServiceImpl.class);
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public FollowServiceImpl(FollowRepository followRepository, UserRepository userRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        logger.debug("FollowServiceImpl initialized with followRepository={} userRepository={}",
                followRepository != null, userRepository != null);
    }

    @Override
    public Follow follow(User follower, User followee) {
        return followRepository.findByFollowerAndFollowee(follower, followee)
                .orElseGet(() -> {
                    Follow f = new Follow();
                    f.setFollower(follower);
                    f.setFollowee(followee);
                    Follow saved = null;
                    try {
                        saved = followRepository.save(f);
                        followRepository.flush();
                        logger.info("Created follow {} -> {} (id={})", follower.getId(), followee.getId(),
                                saved.getId());
                    } catch (Exception ex) {
                        logger.error("Failed to save or flush follow {} -> {}: {}", follower.getId(), followee.getId(),
                                ex.toString());
                        throw new RuntimeException("关注操作失败: " + ex.getMessage(), ex);
                    }
                    if (saved == null || saved.getId() == null) {
                        logger.error("Follow save returned null or id is null for {} -> {}", follower.getId(),
                                followee.getId());
                        throw new RuntimeException("关注操作失败: 未能保存记录");
                    }
                    return saved;
                });
    }

    @Override
    public void unfollow(User follower, User followee) {
        followRepository.findByFollowerAndFollowee(follower, followee)
                .ifPresent(followRepository::delete);
    }

    @Override
    public boolean isFollowing(User follower, User followee) {
        return followRepository.findByFollowerAndFollowee(follower, followee).isPresent();
    }

    @Override
    public boolean areFriends(User a, User b) {
        return isFollowing(a, b) && isFollowing(b, a);
    }

    @Override
    public List<User> listFollowers(User user) {
        return followRepository.findByFollowee(user).stream()
                .map(Follow::getFollower)
                .collect(Collectors.toList());
    }

    @Override
    public List<User> listFollowing(User user) {
        return followRepository.findByFollower(user).stream()
                .map(Follow::getFollowee)
                .collect(Collectors.toList());
    }

    @Override
    public List<Object[]> pageFollowers(User user, Pageable pageable) {
        return followRepository.findFollowersWithProfile(user, pageable);
    }

    @Override
    public List<Object[]> pageFollowing(User user, Pageable pageable) {
        return followRepository.findFollowingWithProfile(user, pageable);
    }

    @Override
    public long countFollowers(User user) {
        return followRepository.countByFollowee(user);
    }

    @Override
    public long countFollowing(User user) {
        return followRepository.countByFollower(user);
    }

    // no-op: keep the logger used in constructor; remove unused helper method
}
