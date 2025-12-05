package com.kirisamemarisa.blog.repository;

import com.kirisamemarisa.blog.model.PrivateMessage;
import com.kirisamemarisa.blog.model.PrivateMessageStatus;
import com.kirisamemarisa.blog.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivateMessageStatusRepository extends JpaRepository<PrivateMessageStatus, Long> {

    Optional<PrivateMessageStatus> findByMessageAndUser(PrivateMessage message, User user);

    List<PrivateMessageStatus> findByMessageInAndUser(List<PrivateMessage> messages, User user);

    List<PrivateMessageStatus> findByMessage(PrivateMessage message);
}
