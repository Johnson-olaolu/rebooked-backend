package com.personal.rebooked.chat.dto;

import com.personal.rebooked.utils.Constants;

public record MessageDto(
        String senderId,
        String receiverId,
        String Message,
        Constants.ChatType chatType,
        boolean isDelivered,
        boolean isSeen
) {
}
