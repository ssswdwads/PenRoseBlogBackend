package com.kirisamemarisa.blog.dto;

public class BlogPostCreateDTO {
    private String title;
    private String content;
    private Long userId;
    private String coverImageUrl;
    private String directory;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
}
