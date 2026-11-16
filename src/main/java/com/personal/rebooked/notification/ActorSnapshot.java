package com.personal.rebooked.notification;

import com.personal.rebooked.user.dto.PublicUserDTO;
import com.personal.rebooked.user.models.User;

/** Who caused a notification. Never an email or phone number. */
public record ActorSnapshot(String id, String userName, String profilePictureUrl) {
    public static ActorSnapshot of(User user) {
        PublicUserDTO p = PublicUserDTO.from(user);
        return p == null ? null : new ActorSnapshot(p.id(), p.userName(), p.profilePictureUrl());
    }
}
