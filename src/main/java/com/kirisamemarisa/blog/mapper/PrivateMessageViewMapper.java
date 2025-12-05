//把 PrivateMessage + PrivateMessageStatus 转为对当前用户可见的 PrivateMessageViewDTO，并根据撤回状态生成不同的展示文本。
package com.kirisamemarisa.blog.mapper;

import com.kirisamemarisa.blog.dto.PrivateMessageViewDTO;
import com.kirisamemarisa.blog.model.PrivateMessage;
import com.kirisamemarisa.blog.model.PrivateMessageStatus;
import com.kirisamemarisa.blog.model.User;

public class PrivateMessageViewMapper {

    public static PrivateMessageViewDTO toViewDTO(PrivateMessage message,
                                                  PrivateMessageStatus status,
                                                  User currentUser) {
        if (message == null) return null;
        PrivateMessageViewDTO dto = new PrivateMessageViewDTO();
        dto.setId(message.getId());
        dto.setSenderId(message.getSender() != null ? message.getSender().getId() : null);
        dto.setReceiverId(message.getReceiver() != null ? message.getReceiver().getId() : null);
        dto.setMediaUrl(message.getMediaUrl());
        dto.setType(message.getType());
        dto.setCreatedAt(message.getCreatedAt());

        boolean recalled = status != null && status.isRecalled();
        boolean deletedForUser = status != null && status.isDeletedForUser();
        dto.setRecalled(recalled);
        dto.setDeletedForCurrentUser(deletedForUser);

        // 生成展示文本
        if (recalled) {
            Long currentId = currentUser != null ? currentUser.getId() : null;
            Long senderId = message.getSender() != null ? message.getSender().getId() : null;
            if (currentId != null && currentId.equals(senderId)) {
                dto.setDisplayText("你撤回了一条消息");
            } else {
                dto.setDisplayText("对方撤回了一条消息");
            }
            // 撤回后原始内容不再显示
            dto.setText(null);
        } else {
            dto.setText(message.getText());
            dto.setDisplayText(message.getText());
        }

        return dto;
    }
}
