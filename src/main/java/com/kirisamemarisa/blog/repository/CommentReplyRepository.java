package com.kirisamemarisa.blog.repository;

import com.kirisamemarisa.blog.model.CommentReply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentReplyRepository extends JpaRepository<CommentReply, Long> {

    Page<CommentReply> findByCommentIdOrderByCreatedAtDesc(Long commentId, Pageable pageable);
}
