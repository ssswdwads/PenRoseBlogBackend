package com.kirisamemarisa.blog.controller;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.CommentCreateDTO;
import com.kirisamemarisa.blog.dto.CommentDTO;
import com.kirisamemarisa.blog.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/comment")
public class CommentController {
    @Autowired
    private CommentService commentService;

    @PostMapping
    public ApiResponse<Long> addComment(@RequestBody CommentCreateDTO dto) {
        return commentService.addComment(dto);
    }

    @GetMapping("/list/{blogPostId}")
    public ApiResponse<List<CommentDTO>> listComments(@PathVariable Long blogPostId, @RequestParam(required = false) Long currentUserId) {
        List<CommentDTO> list = commentService.listComments(blogPostId, currentUserId);
        return new ApiResponse<>(200, "获取成功", list);
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Boolean> deleteComment(@PathVariable Long commentId, @RequestParam Long userId) {
        return commentService.deleteComment(commentId, userId);
    }

    @PostMapping("/{commentId}/like")
    public ApiResponse<Boolean> toggleLike(@PathVariable Long commentId, @RequestParam Long userId) {
        return commentService.toggleLike(commentId, userId);
    }
}
