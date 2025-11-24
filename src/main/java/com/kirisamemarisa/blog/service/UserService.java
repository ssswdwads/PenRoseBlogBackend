package com.kirisamemarisa.blog.service;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.common.JwtUtil;
import com.kirisamemarisa.blog.dto.UserLoginDTO;
import com.kirisamemarisa.blog.dto.UserRegisterDTO;
import com.kirisamemarisa.blog.dto.UserProfileDTO;
import com.kirisamemarisa.blog.mapper.UserMapper;
import com.kirisamemarisa.blog.mapper.UserProfileMapper;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.model.UserProfile;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.repository.UserProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    /*
    这里的 userMapper 对象，
    实际上是由 Spring 容器自动注入的 MapStruct 框架生成的 UserMapperImpl 实现类实例。
    具体原理如下：
    你定义了 UserMapper 接口，并用 @Mapper(componentModel = "spring") 注解。
    MapStruct 在编译时自动生成 UserMapperImpl 实现类，负责 DTO 和实体的转换。
    因为 componentModel = "spring"，MapStruct 生成的实现类会被注册为 Spring Bean。
    所以 @Autowired 注入的 userMapper 实际就是 UserMapperImpl 的实例，可以直接调用接口方法实现自动转换。
    */
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ApiResponse<Void> register(UserRegisterDTO userRegisterDTO) {
        String username = userRegisterDTO.getUsername();
        String password = userRegisterDTO.getPassword();
        // 用户名校验
        if (username == null || !username.matches("^[A-Za-z0-9_]{5,15}$")) {
            return new ApiResponse<>(400, "用户名必须为5-15位，仅支持数字、字母、下划线", null);
        }
        // 密码校验
        if (password == null || !password.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,12}$")) {
            return new ApiResponse<>(400, "密码必须为8-12位，且包含数字和字母，不允许其他字符", null);
        }
        if (userRepository.findByUsername(username) != null) {
            return new ApiResponse<>(400, "用户名已存在", null);
        }
        User user = userMapper.toUser(userRegisterDTO);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        return new ApiResponse<>(200, "注册成功", null);
    }

    public ApiResponse<String> login(UserLoginDTO userLoginDTO) {
        User dbUser = userRepository.findByUsername(userLoginDTO.getUsername());
        if (dbUser == null) {
            return new ApiResponse<>(400, "用户不存在", null);
        }
        if (!passwordEncoder.matches(userLoginDTO.getPassword(), dbUser.getPassword())) {
            return new ApiResponse<>(400, "密码错误", null);
        }
        String token = JwtUtil.generateToken(dbUser.getUsername());
        return new ApiResponse<>(200, "登录成功", token);
    }

    public UserProfileDTO getUserProfileDTO(Long userId) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId);
        if (userProfile == null) {
            return null;
        }
        return userProfileMapper.toDTO(userProfile);
    }

    public boolean updateUserProfile(UserProfileDTO userProfileDTO) {
        if (userProfileDTO == null || userProfileDTO.getId() == null) {
            return false;
        }
        UserProfile userProfile = userProfileRepository.findByUserId(userProfileDTO.getId());
        if (userProfile == null) {
            return false;
        }
        userProfile.setNickname(userProfileDTO.getNickname());
        userProfile.setAvatarUrl(userProfileDTO.getAvatarUrl());
        userProfile.setBackgroundUrl(userProfileDTO.getBackgroundUrl());
        // 性别字段属于User表
        if (userProfile.getUser() != null && userProfileDTO.getGender() != null) {
            userProfile.getUser().setGender(userProfileDTO.getGender());
        }
        userProfileRepository.save(userProfile);
        return true;
    }
}


