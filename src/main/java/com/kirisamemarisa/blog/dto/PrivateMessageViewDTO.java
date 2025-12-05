//对话展示时使用的视图DTO，包含撤回和删除后需要展示的文本

package com.kirisamemarisa.blog.dto;

import com.kirisamemarisa.blog.model.PrivateMessage.MessageType;

import java.time.Instant;

public class PrivateMessageViewDTO {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String text;
    private String mediaUrl;
    private MessageType type;
    private Instant createdAt;

    // 撤回、删除相关
    private boolean recalled;
    private boolean deletedForCurrentUser;
    // 给当前用户看到的展示文本，如「你撤回了一条消息」/「对方撤回了一条消息」
    private String displayText;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRecalled() {
        return recalled;
    }

    public void setRecalled(boolean recalled) {
        this.recalled = recalled;
    }

    public boolean isDeletedForCurrentUser() {
        return deletedForCurrentUser;
    }

    public void setDeletedForCurrentUser(boolean deletedForCurrentUser) {
        this.deletedForCurrentUser = deletedForCurrentUser;
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }
}
