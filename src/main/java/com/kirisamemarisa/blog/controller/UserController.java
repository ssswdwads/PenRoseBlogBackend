package com.kirisamemarisa.blog.controller;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.UserLoginDTO;
import com.kirisamemarisa.blog.dto.UserRegisterDTO;
import com.kirisamemarisa.blog.dto.UserProfileDTO;
import com.kirisamemarisa.blog.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

    // 注册接口
    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody UserRegisterDTO userRegisterDTO) {
        return userService.register(userRegisterDTO);
    }

    // 登录接口
    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody UserLoginDTO userLoginDTO) {
        return userService.login(userLoginDTO);
    }

    // 获取用户个人信息
    @GetMapping("/profile/{userId}")
    public ApiResponse<UserProfileDTO> getProfile(@PathVariable Long userId) {
        UserProfileDTO dto = userService.getUserProfileDTO(userId);
        if (dto == null) {
            return new ApiResponse<>(404, "用户信息不存在", null);
        }
        return new ApiResponse<>(200, "获取成功", dto);
    }

    // 更新用户个人信息
    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(@RequestBody UserProfileDTO userProfileDTO) {
        boolean success = userService.updateUserProfile(userProfileDTO);
        if (success) {
            return new ApiResponse<>(200, "更新成功", null);
        } else {
            return new ApiResponse<>(400, "更新失败", null);
        }
    }
}
