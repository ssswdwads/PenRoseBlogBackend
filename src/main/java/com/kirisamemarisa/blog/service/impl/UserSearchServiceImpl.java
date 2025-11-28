package com.kirisamemarisa.blog.service.impl;

import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.model.UserProfile;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.service.UserSearchService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserSearchServiceImpl implements UserSearchService {
    private final UserRepository userRepository;

    public UserSearchServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<Object[]> searchByUsernameWithProfile(String username) {
        return userRepository.searchByUsernameWithProfile(username);
    }

    @Override
    public List<Object[]> searchByNicknameWithProfile(String nickname) {
        return userRepository.searchByNicknameWithProfile(nickname);
    }
}

