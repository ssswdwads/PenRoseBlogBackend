package com.kirisamemarisa.blog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.kirisamemarisa.blog.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);

    @Query("SELECT u, up FROM User u LEFT JOIN UserProfile up ON u.id = up.id WHERE u.username LIKE %:username%")
    List<Object[]> searchByUsernameWithProfile(@Param("username") String username);

    @Query("SELECT u, up FROM User u LEFT JOIN UserProfile up ON u.id = up.id WHERE up.nickname LIKE %:nickname%")
    List<Object[]> searchByNicknameWithProfile(@Param("nickname") String nickname);
}
