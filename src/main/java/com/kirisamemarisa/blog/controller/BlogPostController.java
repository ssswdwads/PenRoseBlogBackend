package com.kirisamemarisa.blog.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.*;
import com.kirisamemarisa.blog.service.BlogPostService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;

@RestController
@RequestMapping("/api/blogpost")
public class BlogPostController {
    private static final Logger logger = LoggerFactory.getLogger(BlogPostController.class);

    private final BlogPostService blogPostService;

    @Value("${resource.sources-location}")
    private String sourcesLocation;

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

    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable Long id, @RequestBody BlogPostUpdateDTO dto) {
        return blogPostService.update(id, dto);
    }

    @PostMapping("/withcover")
    public ApiResponse<Long> createWithCover(@RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "directory", required = false) String directory,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {
        return blogPostService.createWithCover(title, content, userId, directory, cover);
    }

    @PutMapping("/{id}/withcover")
    public ApiResponse<Boolean> updateWithCover(@PathVariable Long id,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "directory", required = false) String directory,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {
        return blogPostService.updateWithCover(id, content, directory, cover);
    }

    /**
     * 上传媒体文件（图片 / gif / video）供编辑器内使用，返回可访问的 URL
     * 返回格式：ApiResponse<String>，data 为 url（以 /sources/... 开头）
     */
    @PostMapping("/media")
    public ApiResponse<String> uploadMedia(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId) {
        if (file == null || file.isEmpty())
            return new ApiResponse<>(400, "文件为空", null);
        if (userId != null && userId <= 0) {
            return new ApiResponse<>(400, "非法的 userId", null);
        }
        // use configured sourcesLocation and append blogpostmedia/
        Path baseDir = Paths.get(toLocalPath(sourcesLocation)).toAbsolutePath().normalize();
        String userSegment = userId != null ? Long.toString(userId) : "common";
        Path dirPath = baseDir.resolve("blogpostmedia").resolve(userSegment).normalize();
        try {
            // ensure dirPath is still under baseDir
            if (!dirPath.startsWith(baseDir)) {
                logger.warn("目标目录不在允许范围内: {} (base: {})", dirPath, baseDir);
                return new ApiResponse<>(400, "非法的目标目录", null);
            }
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            logger.error("无法创建目录: {}", dirPath, e);
            return new ApiResponse<>(500, "上传失败（无法创建目录）", null);
        }
        String rawName = file.getOriginalFilename();
        String safeName = sanitizeFilename(rawName);
        if (safeName.isEmpty()) safeName = String.valueOf(System.currentTimeMillis());
        String fileName = System.currentTimeMillis() + "_" + safeName;
        Path destPath = dirPath.resolve(fileName).normalize();
        try {
            Path allowed = dirPath.toAbsolutePath().normalize();
            if (!destPath.startsWith(allowed)) {
                logger.warn("尝试写入不允许的位置: {} (allowed: {})", destPath, allowed);
                return new ApiResponse<>(400, "非法的文件路径", null);
            }
            File destFile = destPath.toFile();
            file.transferTo(destFile);
            String url = "/sources/blogpostmedia/" + (userId != null ? userSegment + "/" : "common/") + fileName;
            return new ApiResponse<>(200, "上传成功", url);
        } catch (IOException e) {
            logger.error("上传媒体失败", e);
            return new ApiResponse<>(500, "上传失败", null);
        }
    }

    private String toLocalPath(String configured) {
        if (configured == null) return "";
        String v = configured;
        if (v.startsWith("file:")) v = v.substring(5);
        if (!v.endsWith(File.separator) && !v.endsWith("/")) {
            v = v + File.separator;
        }
        return v.replace('/', File.separatorChar);
    }

    private String sanitizeFilename(String raw) {
        if (raw == null) return "";
        // Remove any path segments by stripping characters before last slash/backslash
        String name = raw;
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (idx >= 0 && idx + 1 < name.length()) name = name.substring(idx + 1);
        name = name.replace("..", "");
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.length() > 200) name = name.substring(name.length() - 200);
        return name;
    }
}
