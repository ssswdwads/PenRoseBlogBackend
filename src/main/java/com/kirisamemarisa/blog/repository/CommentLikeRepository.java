package com.kirisamemarisa.blog.repository;

import com.kirisamemarisa.blog.model.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByCommentIdAndUserId(Long commentId, Long userId);
    long countByCommentId(Long commentId);

    // 新增：根据评论 ID 删除点赞
    void deleteByCommentId(Long commentId);

    // 新增：根据多条评论批量删除点赞
    void deleteByCommentIdIn(List<Long> commentIds);
}