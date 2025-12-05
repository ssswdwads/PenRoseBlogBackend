package com.kirisamemarisa.blog.repository;

import com.kirisamemarisa.blog.model.CommentReply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentReplyRepository extends JpaRepository<CommentReply, Long> {

    Page<CommentReply> findByCommentIdOrderByCreatedAtDesc(Long commentId, Pageable pageable);

    // 新增：根据评论 ID 删除所有回复
    void deleteByCommentId(Long commentId);

    // 新增：根据多条评论批量删除回复
    void deleteByCommentIdIn(List<Long> commentIds);
}