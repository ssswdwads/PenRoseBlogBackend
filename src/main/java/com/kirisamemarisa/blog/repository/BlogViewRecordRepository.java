package com.kirisamemarisa.blog.repository;

import com.kirisamemarisa.blog.model.BlogViewRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogViewRecordRepository extends JpaRepository<BlogViewRecord, Long> {
}
