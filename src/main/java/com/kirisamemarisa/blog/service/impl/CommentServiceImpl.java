package com.kirisamemarisa.blog.service.impl;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.CommentCreateDTO;
import com.kirisamemarisa.blog.dto.CommentDTO;
import com.kirisamemarisa.blog.mapper.CommentMapper;
import com.kirisamemarisa.blog.model.BlogPost;
import com.kirisamemarisa.blog.model.Comment;
import com.kirisamemarisa.blog.model.CommentLike;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.model.UserProfile;
import com.kirisamemarisa.blog.repository.BlogPostRepository;
import com.kirisamemarisa.blog.repository.CommentLikeRepository;
import com.kirisamemarisa.blog.repository.CommentRepository;
import com.kirisamemarisa.blog.repository.UserProfileRepository;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl implements CommentService {
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private BlogPostRepository blogPostRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommentLikeRepository commentLikeRepository;
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public ApiResponse<Long> addComment(CommentCreateDTO dto) {
        if (dto == null || dto.getBlogPostId() == null || dto.getUserId() == null || dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return new ApiResponse<>(400, "参数不完整", null);
        }
        Optional<BlogPost> blogPostOpt = blogPostRepository.findById(dto.getBlogPostId());
        Optional<User> userOpt = userRepository.findById(dto.getUserId());
        if (blogPostOpt.isEmpty() || userOpt.isEmpty()) {
            return new ApiResponse<>(404, "博客或用户不存在", null);
        }
        Comment comment = new Comment();
        comment.setBlogPost(blogPostOpt.get());
        comment.setUser(userOpt.get());
        comment.setContent(dto.getContent());
        commentRepository.save(comment);
        // 更新博客评论数
        BlogPost blogPost = blogPostOpt.get();
        blogPost.setCommentCount(blogPost.getCommentCount() + 1);
        blogPostRepository.save(blogPost);
        return new ApiResponse<>(200, "评论成功", comment.getId());
    }

    @Override
    public List<CommentDTO> listComments(Long blogPostId, Long currentUserId) {
        List<Comment> comments = commentRepository.findByBlogPostIdOrderByCreatedAtDesc(blogPostId);
        return comments.stream().map(comment -> {
            CommentDTO dto = commentMapper.toDTO(comment);
            if (currentUserId != null) {
                dto.setLikedByCurrentUser(commentLikeRepository.findByCommentIdAndUserId(comment.getId(), currentUserId).isPresent());
            } else {
                dto.setLikedByCurrentUser(false);
            }
            // 补全nickname和avatarUrl
            userProfileRepository.findById(comment.getUser().getId()).ifPresent(profile -> {
                dto.setNickname(profile.getNickname());
                dto.setAvatarUrl(profile.getAvatarUrl());
            });
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ApiResponse<Boolean> deleteComment(Long commentId, Long userId) {
        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) {
            return new ApiResponse<>(404, "评论不存在", false);
        }
        Comment comment = commentOpt.get();
        if (!comment.getUser().getId().equals(userId)) {
            return new ApiResponse<>(403, "无权限删除", false);
        }
        commentRepository.delete(comment);
        // 更新博客评论数
        BlogPost blogPost = comment.getBlogPost();
        blogPost.setCommentCount(Math.max(0, blogPost.getCommentCount() - 1));
        blogPostRepository.save(blogPost);
        return new ApiResponse<>(200, "删除成功", true);
    }

    @Override
    @Transactional
    public ApiResponse<Boolean> toggleLike(Long commentId, Long userId) {
        Optional<CommentLike> likeOpt = commentLikeRepository.findByCommentIdAndUserId(commentId, userId);
        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) return new ApiResponse<>(404, "评论不存在", false);
        Comment comment = commentOpt.get();
        if (likeOpt.isPresent()) {
            commentLikeRepository.delete(likeOpt.get());
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            commentRepository.save(comment);
            return new ApiResponse<>(200, "取消点赞", false);
        } else {
            CommentLike like = new CommentLike();
            like.setComment(comment);
            like.setUser(userRepository.findById(userId).orElse(null));
            commentLikeRepository.save(like);
            comment.setLikeCount(comment.getLikeCount() + 1);
            commentRepository.save(comment);
            return new ApiResponse<>(200, "点赞成功", true);
        }
    }
}
