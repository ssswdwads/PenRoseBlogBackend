package com.kirisamemarisa.blog.service.impl;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.BlogViewRecordCreateDTO;
import com.kirisamemarisa.blog.dto.BlogViewStatsDTO;
import com.kirisamemarisa.blog.mapper.BlogViewStatsMapper;
import com.kirisamemarisa.blog.model.BlogPost;
import com.kirisamemarisa.blog.model.BlogViewRecord;
import com.kirisamemarisa.blog.model.BlogViewStats;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.repository.BlogPostRepository;
import com.kirisamemarisa.blog.repository.BlogViewRecordRepository;
import com.kirisamemarisa.blog.repository.BlogViewStatsRepository;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.service.BlogViewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BlogViewServiceImpl implements BlogViewService {

    private static final Logger logger = LoggerFactory.getLogger(BlogViewServiceImpl.class);

    private final BlogPostRepository blogPostRepository;
    private final UserRepository userRepository;
    private final BlogViewRecordRepository blogViewRecordRepository;
    private final BlogViewStatsRepository blogViewStatsRepository;
    private final BlogViewStatsMapper blogViewStatsMapper;

    public BlogViewServiceImpl(BlogPostRepository blogPostRepository,
                               UserRepository userRepository,
                               BlogViewRecordRepository blogViewRecordRepository,
                               BlogViewStatsRepository blogViewStatsRepository,
                               BlogViewStatsMapper blogViewStatsMapper) {
        this.blogPostRepository = blogPostRepository;
        this.userRepository = userRepository;
        this.blogViewRecordRepository = blogViewRecordRepository;
        this.blogViewStatsRepository = blogViewStatsRepository;
        this.blogViewStatsMapper = blogViewStatsMapper;
    }

    @Override
    @Transactional
    public ApiResponse<BlogViewStatsDTO> recordView(BlogViewRecordCreateDTO dto) {
        if (dto == null || dto.getBlogPostId() == null) {
            return new ApiResponse<>(400, "blogPostId 不能为空", null);
        }

        Optional<BlogPost> postOpt = blogPostRepository.findById(dto.getBlogPostId());
        if (postOpt.isEmpty()) {
            return new ApiResponse<>(404, "博客不存在", null);
        }
        BlogPost post = postOpt.get();

        User user = null;
        if (dto.getUserId() != null) {
            user = userRepository.findById(dto.getUserId()).orElse(null);
        }

        // 1. 记录一条浏览明细
        BlogViewRecord record = new BlogViewRecord();
        record.setBlogPost(post);
        record.setUser(user);
        blogViewRecordRepository.save(record);

        // 2. 增加浏览统计
        BlogViewStats stats = blogViewStatsRepository.findByBlogPostId(post.getId())
                .orElseGet(() -> {
                    BlogViewStats s = new BlogViewStats();
                    s.setBlogPost(post);
                    s.setViewCount(0L);
                    return s;
                });
        Long current = stats.getViewCount() == null ? 0L : stats.getViewCount();
        stats.setViewCount(current + 1L);
        stats = blogViewStatsRepository.save(stats);

        BlogViewStatsDTO statsDTO = blogViewStatsMapper.toDTO(stats);
        return new ApiResponse<>(200, "浏览记录成功", statsDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<BlogViewStatsDTO> getStats(Long blogPostId) {
        if (blogPostId == null) {
            return new ApiResponse<>(400, "blogPostId 不能为空", null);
        }
        Optional<BlogViewStats> statsOpt = blogViewStatsRepository.findByBlogPostId(blogPostId);
        if (statsOpt.isEmpty()) {
            // 没有记录时默认 0
            BlogViewStatsDTO dto = new BlogViewStatsDTO();
            dto.setBlogPostId(blogPostId);
            dto.setViewCount(0L);
            return new ApiResponse<>(200, "获取成功", dto);
        }
        BlogViewStatsDTO dto = blogViewStatsMapper.toDTO(statsOpt.get());
        return new ApiResponse<>(200, "获取成功", dto);
    }

    @Override
    @Transactional
    public void deleteByBlogPostId(Long blogPostId) {
        if (blogPostId == null) return;

        try {
            // 先删明细，再删统计，避免外键约束
            blogViewRecordRepository.deleteByBlogPost_Id(blogPostId);
        } catch (Exception e) {
            logger.warn("删除博客 {} 的浏览明细失败", blogPostId, e);
        }

        try {
            blogViewStatsRepository.deleteByBlogPost_Id(blogPostId);
        } catch (Exception e) {
            logger.warn("删除博客 {} 的浏览统计失败", blogPostId, e);
        }
    }

}