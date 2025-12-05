package com.kirisamemarisa.blog.dto;

public class BlogPostUpdateDTO {
    private String coverImageUrl;
    private String content;
    private String directory;

    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
}
