package com.kirisamemarisa.blog.controller;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.*;
import com.kirisamemarisa.blog.service.BlogPostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/blogpost")
public class BlogPostController {

    private final BlogPostService blogPostService;

    public BlogPostController(BlogPostService blogPostService) {
        this.blogPostService = blogPostService;
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestBody BlogPostCreateDTO dto) {
        return blogPostService.create(dto);
    }

    @GetMapping("/{id}")
    public ApiResponse<BlogPostDTO> get(@PathVariable Long id,
                                        @RequestParam(required = false) Long currentUserId) {
        BlogPostDTO dto = blogPostService.getById(id, currentUserId);
        if (dto == null) {
            return new ApiResponse<>(404, "博客不存在", null);
        }
        return new ApiResponse<>(200, "获取成功", dto);
    }

    @GetMapping
    public ApiResponse<PageResult<BlogPostDTO>> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) Long currentUserId) {
        PageResult<BlogPostDTO> result = blogPostService.pageList(page, size, currentUserId);
        return new ApiResponse<>(200, "获取成功", result);
    }

    @PostMapping("/{id}/like")
    public ApiResponse<Boolean> toggleLike(@PathVariable Long id,
                                           @RequestParam Long userId) {
        return blogPostService.toggleLike(id, userId);
    }

    @PostMapping("/comment")
    public ApiResponse<Long> comment(@RequestBody CommentCreateDTO dto) {
        return blogPostService.addComment(dto);
    }

    @GetMapping("/{id}/comments")
    public ApiResponse<PageResult<CommentDTO>> comments(@PathVariable Long id,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) Long currentUserId) {
        PageResult<CommentDTO> result = blogPostService.pageComments(id, page, size, currentUserId);
        return new ApiResponse<>(200, "获取成功", result);
    }

    @PostMapping("/comment/{id}/like")
    public ApiResponse<Boolean> toggleCommentLike(@PathVariable Long id,
                                                  @RequestParam Long userId) {
        return blogPostService.toggleCommentLike(id, userId);
    }

    @PostMapping("/repost")
    public ApiResponse<Long> repost(@RequestBody RepostCreateDTO dto) {
        return blogPostService.repost(dto);
    }

    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable Long id, @RequestBody BlogPostUpdateDTO dto) {
        return blogPostService.update(id, dto);
    }
}
