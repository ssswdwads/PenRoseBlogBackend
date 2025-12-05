//记录每条消息对每个用户的状态，比如是否撤回，是否本地删除等
package com.kirisamemarisa.blog.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "private_message_status")
public class PrivateMessageStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 对应的私信
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id")
    private PrivateMessage message;

    // 此条状态针对哪个用户（发送方或接收方）
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // 是否已撤回：撤回是全局概念，但这里记录在哪个用户下是否已经同步/应用，可以按需扩展
    @Column(nullable = false)
    private boolean recalled = false;

    // 是否对该用户「本地删除」
    @Column(nullable = false)
    private boolean deletedForUser = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public PrivateMessage getMessage() {
        return message;
    }

    public void setMessage(PrivateMessage message) {
        this.message = message;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isRecalled() {
        return recalled;
    }

    public void setRecalled(boolean recalled) {
        this.recalled = recalled;
    }

    public boolean isDeletedForUser() {
        return deletedForUser;
    }

    public void setDeletedForUser(boolean deletedForUser) {
        this.deletedForUser = deletedForUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
