package com.kirisamemarisa.blog.repository;

import com.kirisamemarisa.blog.model.BlogPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    // 用于校验“只有作者能删除”
    Optional<BlogPost> findByIdAndUserId(Long id, Long userId);
}