package com.personal.rebooked.security;

import java.security.Principal;

/** The socket user. The name is the user id, which is what convertAndSendToUser routes by. */
public record StompPrincipal(String userId) implements Principal {
    @Override
    public String getName() {
        return userId;
    }
}
