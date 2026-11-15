package com.personal.rebooked.security;

import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.user.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Access to the authenticated user from controllers. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<User> optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** The logged-in user, or 401. */
    public static User require() {
        return optional().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }
}
