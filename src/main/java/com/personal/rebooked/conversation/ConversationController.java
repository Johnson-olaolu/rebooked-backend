package com.personal.rebooked.conversation;

import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.utils.ResponseHandler;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Paged chat reads. Writes and live events go over STOMP (ConversationGateway). */
@RequiredArgsConstructor
@RestController
@RequestMapping("/conversation")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {
    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(required = false) String filter,
                                       @RequestParam(required = false) String search,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer pageSize) {
        return ResponseHandler.generateResponse(
                conversationService.list(me(), filter, search, page, pageSize), "Conversations fetched successfully");
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Object> unreadCount() {
        return ResponseHandler.generateResponse(Map.of("total", conversationService.unreadTotal(me())),
                "Unread count fetched successfully");
    }

    /** The caller's conversation with a book's seller (?bookId=) or a user (?userId=); data is null if none. */
    @GetMapping("/with")
    public ResponseEntity<Object> with(@RequestParam(required = false) String bookId,
                                       @RequestParam(required = false) String userId) {
        return ResponseHandler.generateResponse(conversationService.with(me(), bookId, userId),
                "Conversation fetched successfully");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> get(@PathVariable String id) {
        return ResponseHandler.generateResponse(conversationService.get(me(), id), "Conversation fetched successfully");
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<Object> messages(@PathVariable String id,
                                           @RequestParam(required = false) String before,
                                           @RequestParam(required = false) Integer limit) {
        return ResponseHandler.generateResponse(conversationService.messages(me(), id, before, limit),
                "Messages fetched successfully");
    }

    private static String me() {
        return CurrentUser.require().getId();
    }
}
