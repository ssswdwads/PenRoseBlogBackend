package com.kirisamemarisa.blog.service;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.*;
import com.kirisamemarisa.blog.dto.PageResult;

import java.util.List;

public interface BlogPostService {
    ApiResponse<Long> create(BlogPostCreateDTO dto);
    BlogPostDTO getById(Long id, Long currentUserId);
    ApiResponse<Boolean> update(Long id, BlogPostUpdateDTO dto);
    ApiResponse<Boolean> toggleLike(Long blogPostId, Long userId);
    ApiResponse<Boolean> toggleCommentLike(Long commentId, Long userId);
    ApiResponse<Long> addComment(CommentCreateDTO dto);
    List<CommentDTO> listComments(Long blogPostId, Long currentUserId);
    List<BlogPostDTO> list(int page, int size, Long currentUserId);
    ApiResponse<Long> repost(RepostCreateDTO dto);
    PageResult<BlogPostDTO> pageList(int page, int size, Long currentUserId);
    PageResult<CommentDTO> pageComments(Long blogPostId, int page, int size, Long currentUserId);
}
