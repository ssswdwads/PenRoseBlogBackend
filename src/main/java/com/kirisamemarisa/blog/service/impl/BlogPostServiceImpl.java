package com.kirisamemarisa.blog.service.impl;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.*;
import com.kirisamemarisa.blog.model.*;
import com.kirisamemarisa.blog.repository.*;
import com.kirisamemarisa.blog.service.BlogPostService;
import com.kirisamemarisa.blog.mapper.BlogPostMapper;
import com.kirisamemarisa.blog.service.CommentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.List;

@Service public class BlogPostServiceImpl implements BlogPostService {
    private final BlogPostRepository blogPostRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final BlogPostLikeRepository blogPostLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final UserProfileRepository userProfileRepository;
    private final BlogPostMapper blogPostMapper;
    private final CommentService commentService; // 新增依赖

    public BlogPostServiceImpl(BlogPostRepository blogPostRepository,
                               UserRepository userRepository,
                               CommentRepository commentRepository,
                               BlogPostLikeRepository blogPostLikeRepository,
                               CommentLikeRepository commentLikeRepository,
                               UserProfileRepository userProfileRepository,
                               BlogPostMapper blogPostMapper,
                               CommentService commentService) { // 注入 CommentService
        this.blogPostRepository = blogPostRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.blogPostLikeRepository = blogPostLikeRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.userProfileRepository = userProfileRepository;
        this.blogPostMapper = blogPostMapper;
        this.commentService = commentService; // 初始化 CommentService
    }

    @Override
    @Transactional
    public ApiResponse<Long> create(BlogPostCreateDTO dto) {
        if (dto == null) return new ApiResponse<>(400, "请求体不能为空", null);
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            return new ApiResponse<>(400, "标题不能为空", null);
        if (dto.getContent() == null || dto.getContent().trim().isEmpty())
            return new ApiResponse<>(400, "正文不能为空", null);
        if (dto.getUserId() == null)
            return new ApiResponse<>(400, "用户ID不能为空", null);
        Optional<User> userOpt = userRepository.findById(dto.getUserId());
        if (userOpt.isEmpty())
            return new ApiResponse<>(404, "用户不存在", null);

        BlogPost post = new BlogPost();
        post.setTitle(dto.getTitle().trim());
        post.setContent(dto.getContent().trim());
        post.setCoverImageUrl(dto.getCoverImageUrl());
        post.setDirectory(dto.getDirectory());
        post.setUser(userOpt.get());
        post.setRepost(false);
        BlogPost saved = blogPostRepository.save(post);
        return new ApiResponse<>(200, "创建成功", saved.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public BlogPostDTO getById(Long id) {
        return blogPostRepository.findById(id).map(p -> blogPostMapper.toDTO(p)).orElse(null);
    }

    @Override
    @Transactional
    public ApiResponse<Boolean> update(Long id, BlogPostUpdateDTO dto) {
        if (dto == null) return new ApiResponse<>(400, "请求体不能为空", false);
        Optional<BlogPost> opt = blogPostRepository.findById(id);
        if (opt.isEmpty()) return new ApiResponse<>(404, "博客不存在", false);
        BlogPost post = opt.get();
        // 支持cover字段兼容
        if (dto.getCoverImageUrl() != null) post.setCoverImageUrl(dto.getCoverImageUrl());
        // 兼容前端传cover字段
        try {
            java.lang.reflect.Field coverField = dto.getClass().getDeclaredField("cover");
            coverField.setAccessible(true);
            Object coverValue = coverField.get(dto);
            if (coverValue instanceof String && !((String)coverValue).isEmpty()) {
                post.setCoverImageUrl((String)coverValue);
            }
        } catch (Exception ignored) {}
        if (dto.getContent() != null && !dto.getContent().trim().isEmpty())
            post.setContent(dto.getContent().trim());
        if (dto.getDirectory() != null) post.setDirectory(dto.getDirectory());
        // 支持后续字段扩展
        blogPostMapper.updateEntityFromDTO(dto, post);
        blogPostRepository.save(post);
        return new ApiResponse<>(200, "更新成功", true);
    }

    @Override
    @Transactional
    public ApiResponse<Boolean> toggleLike(Long blogPostId, Long userId) {
        if (blogPostId == null || userId == null)
            return new ApiResponse<>(400, "参数缺失", false);
        Optional<BlogPost> postOpt = blogPostRepository.findById(blogPostId);
        if (postOpt.isEmpty()) return new ApiResponse<>(404, "博客不存在", false);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return new ApiResponse<>(404, "用户不存在", false);

        BlogPost post = postOpt.get();
        Optional<BlogPostLike> likeOpt = blogPostLikeRepository.findByBlogPostIdAndUserId(blogPostId, userId);
        if (likeOpt.isPresent()) {
            blogPostLikeRepository.delete(likeOpt.get());
            post.setLikeCount(safeLong(post.getLikeCount()) - 1);
            blogPostRepository.save(post);
            return new ApiResponse<>(200, "已取消点赞", false);
        } else {
            BlogPostLike like = new BlogPostLike();
            like.setBlogPost(post);
            like.setUser(userOpt.get());
            blogPostLikeRepository.save(like);
            post.setLikeCount(safeLong(post.getLikeCount()) + 1);
            blogPostRepository.save(post);
            return new ApiResponse<>(200, "点赞成功", true);
        }
    }

    @Override
    public ApiResponse<Long> addComment(CommentCreateDTO dto) {
        return commentService.addComment(dto);
    }

    @Override
    public List<CommentDTO> listComments(Long blogPostId, Long currentUserId) {
        return commentService.listComments(blogPostId, currentUserId);
    }

    @Override
    public ApiResponse<Boolean> toggleCommentLike(Long commentId, Long userId) {
        return commentService.toggleLike(commentId, userId);
    }

    @Override
    @Transactional
    public ApiResponse<Long> repost(RepostCreateDTO dto) {
        if (dto == null) return new ApiResponse<>(400, "请求体不能为空", null);
        if (dto.getOriginalPostId() == null) return new ApiResponse<>(400, "原博客ID不能为空", null);
        if (dto.getUserId() == null) return new ApiResponse<>(400, "用户ID不能为空", null);

        Optional<BlogPost> originalOpt = blogPostRepository.findById(dto.getOriginalPostId());
        if (originalOpt.isEmpty()) return new ApiResponse<>(404, "原博客不存在", null);
        Optional<User> userOpt = userRepository.findById(dto.getUserId());
        if (userOpt.isEmpty()) return new ApiResponse<>(404, "用户不存在", null);

        BlogPost original = originalOpt.get();

        // 原博客正文用于生成转发标题和内容片段
        String originalContent = original.getContent() != null ? original.getContent() : "";
        String snippet = originalContent.length() > 30 ? originalContent.substring(0, 30) : originalContent;

        BlogPost repost = new BlogPost();
        repost.setUser(userOpt.get());
        repost.setRepost(true);
        repost.setOriginalPost(original);
        repost.setTitle(snippet);          // 标题为原文前30字
        repost.setContent(snippet);        // 内容只存片段（与需求“只有正文片段”一致）
        repost.setCoverImageUrl(null);     // 转发不保留封面
        repost.setDirectory(null);         // 不继承目录（需求未要求）
        BlogPost saved = blogPostRepository.save(repost);

        // 原博客转发计数自增（安全处理 null）
        original.setRepostCount(safeInt(original.getRepostCount()) + 1);
        blogPostRepository.save(original);

        return new ApiResponse<>(200, "转发成功", saved.getId());
    }


    @Override
    public List<BlogPostDTO> list(int page, int size, Long currentUserId) {
        // 示例实现，实际可根据业务需求调整
        return blogPostRepository.findAll(PageRequest.of(page, size))
                .stream()
                .map(blogPostMapper::toDTO)
                .toList();
    }

    private CommentDTO toCommentDTO(Comment c, Long currentUserId) {
        CommentDTO dto = new CommentDTO();
        dto.setId(c.getId());
        dto.setBlogPostId(c.getBlogPost().getId());
        dto.setUserId(c.getUser().getId());
        dto.setContent(c.getContent());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setLikeCount(safeLong(c.getLikeCount()));
        if (currentUserId != null) {
            dto.setLikedByCurrentUser(
                    commentLikeRepository.findByCommentIdAndUserId(c.getId(), currentUserId).isPresent()
            );
        }
        userProfileRepository.findById(c.getUser().getId()).ifPresent(p -> {
            dto.setNickname(p.getNickname());
            dto.setAvatarUrl(p.getAvatarUrl());
        });
        return dto;
    }

    private long safeLong(Long v) { return v == null ? 0L : v; }
    private int safeInt(Integer v) { return v == null ? 0 : v; }

}