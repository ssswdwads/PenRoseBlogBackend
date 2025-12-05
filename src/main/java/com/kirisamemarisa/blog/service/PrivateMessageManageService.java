//管理撤回/删除
package com.kirisamemarisa.blog.service;

import com.kirisamemarisa.blog.dto.PrivateMessageViewDTO;
import com.kirisamemarisa.blog.model.User;

import java.util.List;

public interface PrivateMessageManageService {

    // 当前用户撤回一条消息（只能撤回自己发送的，且有两分钟的时间限制）
    void recallMessage(User currentUser, Long messageId);

    // 当前用户删除一条消息（仅在自己视角隐藏）
    void deleteMessageForUser(User currentUser, Long messageId);

    // 按当前用户视角获取与另一方的会话（会自动应用撤回/删除逻辑）
    List<PrivateMessageViewDTO> getConversationView(User currentUser, User otherUser);
}
