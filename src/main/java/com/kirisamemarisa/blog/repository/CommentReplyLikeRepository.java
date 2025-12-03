package com.kirisamemarisa.blog.repository;

import com.kirisamemarisa.blog.model.CommentReplyLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentReplyLikeRepository extends JpaRepository<CommentReplyLike, Long> {

    Optional<CommentReplyLike> findByReplyIdAndUserId(Long replyId, Long userId);

    long countByReplyId(Long replyId);
}
