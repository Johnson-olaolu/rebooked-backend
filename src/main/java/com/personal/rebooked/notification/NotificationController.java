package com.personal.rebooked.notification;

import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.utils.ResponseHandler;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Paged reads and settings. Live events and mark-as-read go over the socket (NotificationGateway). */
@RequiredArgsConstructor
@RestController
@RequestMapping("/notification")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
    public record PreferenceChange(NotificationType type, NotificationChannel channel, Boolean enabled) {}

    public record UnsubscribeRequest(String token) {}

    private final InAppNotifications inApp;
    private final PreferenceService preferences;
    private final UnsubscribeTokens unsubscribeTokens;

    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(required = false) String filter,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer pageSize) {
        return ResponseHandler.generateResponse(inApp.list(me(), filter, page, pageSize),
                "Notifications fetched successfully");
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Object> unreadCount() {
        return ResponseHandler.generateResponse(Map.of("total", inApp.unreadTotal(me())),
                "Unread count fetched successfully");
    }

    @GetMapping("/preferences")
    public ResponseEntity<Object> getPreferences() {
        return ResponseHandler.generateResponse(preferences.list(me()), "Preferences fetched successfully");
    }

    @PatchMapping("/preferences")
    public ResponseEntity<Object> updatePreferences(@RequestBody PreferenceChange change) {
        return ResponseHandler.generateResponse(
                preferences.update(me(), change.type(), change.channel(), change.enabled()),
                "Preferences updated successfully");
    }

    /** Public: the token from an email's unsubscribe link turns that type's email off. */
    @PostMapping("/unsubscribe")
    public ResponseEntity<Object> unsubscribe(@RequestBody(required = false) UnsubscribeRequest request) {
        UnsubscribeTokens.Parsed parsed = unsubscribeTokens.parse(request != null ? request.token() : null);
        if (parsed == null || parsed.type().mandatory()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This unsubscribe link isn't valid",
                    Map.of("token", "Invalid or tampered link"));
        }
        preferences.set(parsed.userId(), parsed.type(), parsed.channel(), false);
        return ResponseHandler.generateResponse(Map.of("type", parsed.type(), "channel", parsed.channel()),
                "You've been unsubscribed");
    }

    private static String me() {
        return CurrentUser.require().getId();
    }
}
