package com.kirisamemarisa.blog.service;

import java.util.List;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.model.UserProfile;

public interface UserSearchService {
    /**
     * 根据用户名模糊搜索，返回User和UserProfile的组合
     */
    List<Object[]> searchByUsernameWithProfile(String username);

    /**
     * 根据昵称模糊搜索，返回User和UserProfile的组合
     */
    List<Object[]> searchByNicknameWithProfile(String nickname);
}

