package com.kirisamemarisa.blog.repository;

import com.kirisamemarisa.blog.model.PrivateMessage;
import com.kirisamemarisa.blog.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {
    List<PrivateMessage> findBySenderAndReceiverOrderByCreatedAtAsc(User sender, User receiver);
    List<PrivateMessage> findByReceiverOrderByCreatedAtDesc(User receiver);
    List<PrivateMessage> findBySenderOrderByCreatedAtDesc(User sender);

    // Fetch join the counterpart user to avoid LazyInitializationException when accessing username later
    @Query("select pm from PrivateMessage pm join fetch pm.receiver r where pm.sender = :sender order by pm.createdAt desc")
    List<PrivateMessage> findBySenderWithReceiverOrderByCreatedAtDesc(@Param("sender") User sender);

    @Query("select pm from PrivateMessage pm join fetch pm.sender s where pm.receiver = :receiver order by pm.createdAt desc")
    List<PrivateMessage> findByReceiverWithSenderOrderByCreatedAtDesc(@Param("receiver") User receiver);

    // Fetch join both sender and receiver for a conversation between two users (ordered asc)
    @Query("select pm from PrivateMessage pm join fetch pm.sender s join fetch pm.receiver r where pm.sender = :sender and pm.receiver = :receiver order by pm.createdAt asc")
    List<PrivateMessage> findBySenderAndReceiverWithParticipantsOrderByCreatedAtAsc(@Param("sender") User sender, @Param("receiver") User receiver);

    @Query("select pm from PrivateMessage pm join fetch pm.sender s join fetch pm.receiver r where pm.sender = :sender and pm.receiver = :receiver order by pm.createdAt asc")
    List<PrivateMessage> findBySenderAndReceiverWithParticipantsOrderByCreatedAtAscReverse(@Param("sender") User sender, @Param("receiver") User receiver);

    @Query("select count(pm) from PrivateMessage pm where pm.receiver.id = :receiverId and pm.readByReceiver = false")
    long countUnreadTotal(@Param("receiverId") Long receiverId);

    @Query("select count(pm) from PrivateMessage pm where pm.receiver.id = :receiverId and pm.sender.id = :senderId and pm.readByReceiver = false")
    long countUnreadBetween(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId);

    @Modifying
    @Query("update PrivateMessage pm set pm.readByReceiver = true " +
           "where pm.sender.id = :senderId and pm.receiver.id = :receiverId and pm.readByReceiver = false")
    int markConversationRead(@Param("senderId") Long senderId, @Param("receiverId") Long receiverId);
}
