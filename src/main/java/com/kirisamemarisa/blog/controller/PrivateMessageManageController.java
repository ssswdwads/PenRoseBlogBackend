//复用现有私信控制器类似的用户解析方式

package com.kirisamemarisa.blog.controller;

import com.kirisamemarisa.blog.common.ApiResponse;
import com.kirisamemarisa.blog.dto.PageResult;
import com.kirisamemarisa.blog.dto.PrivateMessageOperationDTO;
import com.kirisamemarisa.blog.dto.PrivateMessageViewDTO;
import com.kirisamemarisa.blog.model.User;
import com.kirisamemarisa.blog.repository.UserRepository;
import com.kirisamemarisa.blog.service.PrivateMessageManageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class PrivateMessageManageController {

    private final UserRepository userRepository;
    private final PrivateMessageManageService manageService;

    public PrivateMessageManageController(UserRepository userRepository,
                                          PrivateMessageManageService manageService) {
        this.userRepository = userRepository;
        this.manageService = manageService;
    }

    private User resolveCurrent(UserDetails principal, Long headerUserId) {
        if (principal != null) {
            return userRepository.findByUsername(principal.getUsername());
        }
        if (headerUserId != null) {
            return userRepository.findById(headerUserId).orElse(null);
        }
        return null;
    }

    @PostMapping("/recall")
    public ApiResponse<Void> recallMessage(@RequestBody PrivateMessageOperationDTO body,
                                           @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
                                           @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        if (body == null || body.getMessageId() == null) {
            return new ApiResponse<>(400, "messageId不能为空", null);
        }
        try {
            manageService.recallMessage(me, body.getMessageId());
            return new ApiResponse<>(200, "撤回成功", null);
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(404, e.getMessage(), null);
        } catch (IllegalStateException e) {
            // 包括「只能撤回自己发送的消息」「消息发出超过两分钟」等
            return new ApiResponse<>(403, e.getMessage(), null);
        }
    }

    @PostMapping("/delete")
    public ApiResponse<Void> deleteForMe(@RequestBody PrivateMessageOperationDTO body,
                                         @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
                                         @AuthenticationPrincipal UserDetails principal) {
        User me = resolveCurrent(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        if (body == null || body.getMessageId() == null) {
            return new ApiResponse<>(400, "messageId不能为空", null);
        }
        try {
            manageService.deleteMessageForUser(me, body.getMessageId());
            return new ApiResponse<>(200, "删除成功", null);
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(404, e.getMessage(), null);
        }
    }

    // 可选：基于撤回/删除视角的会话列表
    @GetMapping("/conversation/view/{otherId}")
    public ApiResponse<PageResult<PrivateMessageViewDTO>> conversationView(
            @PathVariable Long otherId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(name = "X-User-Id", required = false) Long headerUserId,
            @AuthenticationPrincipal UserDetails principal) {

        User me = resolveCurrent(principal, headerUserId);
        if (me == null) {
            return new ApiResponse<>(401, "未认证", null);
        }
        User other = userRepository.findById(otherId).orElse(null);
        if (other == null) {
            return new ApiResponse<>(404, "用户不存在", null);
        }

        List<PrivateMessageViewDTO> all = manageService.getConversationView(me, other);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<PrivateMessageViewDTO> pageList = all.subList(from, to);
        PageResult<PrivateMessageViewDTO> result =
                new PageResult<>(pageList, all.size(), page, size);
        return new ApiResponse<>(200, "OK", result);
    }
}
