package com.personal.rebooked.notification.providers;

import com.personal.rebooked.notification.*;
import com.personal.rebooked.user.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Subject and body for every type that goes out by email. Account emails with a code or link keep their own
 * templates; everything else uses templates/mail/notification.html.
 */
@Component
@RequiredArgsConstructor
public class EmailContent {
    private final NotificationLinks links;
    private final UnsubscribeTokens unsubscribeTokens;

    public boolean supports(NotificationType type) {
        return switch (type) {
            case NEW_MESSAGES, SOLD_ELSEWHERE, BOOK_UNAVAILABLE, VIEWS_MILESTONE -> false;
            default -> true;
        };
    }

    public EmailMessage render(NotificationRequest r, User recipient) {
        String name = firstName(recipient);
        String title = r.book() != null && r.book().title() != null ? "“" + r.book().title() + "”" : "your book";
        String who = r.actor() != null && r.actor().userName() != null ? r.actor().userName() : "Someone";

        return switch (r.type()) {
            case EMAIL_VERIFICATION -> new EmailMessage("Your Rebooked verification code", "email_verification",
                    Map.of("name", name, "token", String.valueOf(r.data("code"))));
            case PASSWORD_RESET -> new EmailMessage("Reset your Rebooked password", "forgot_password",
                    Map.of("name", name, "url", String.valueOf(r.data("url"))));
            case PASSWORD_CHANGED -> email(r, recipient, "Your Rebooked password was changed",
                    "Your password was changed",
                    List.of("The password for your Rebooked account was just changed.",
                            "If this wasn't you, reset your password straight away and contact us."),
                    null, "Go to Rebooked", links.home());
            case WELCOME -> email(r, recipient, "Welcome to Rebooked, " + name + "!", "Welcome to Rebooked",
                    List.of("Your email is confirmed. You can now list the books you've finished with and chat with buyers and sellers near you."),
                    null, "Set up your profile", links.welcome());
            case ONBOARDING_REMINDER -> email(r, recipient, "Finish setting up your Rebooked profile",
                    "You're almost there",
                    List.of("Add a display name and your city so buyers and sellers nearby can find you."),
                    null, "Finish setting up", links.welcome());
            case BOOK_ENQUIRY -> email(r, recipient, who + " is interested in " + title,
                    "New enquiry about " + title,
                    List.of(who + " started a conversation about " + title + ". Reply quickly: buyers often message more than one seller."),
                    r.preview(), "Reply", links.conversation(r.conversationId()));
            case UNREPLIED_MESSAGE -> email(r, recipient, who + " is waiting for your reply",
                    who + " sent you a message",
                    List.of(who + " messaged you" + (r.book() != null ? " about " + title : "") + " and is waiting for a reply."),
                    r.preview(), "Reply now", links.conversation(r.conversationId()));
            case SOLD_TO_YOU -> email(r, recipient, title + " is yours", "Sold to you",
                    List.of(who + " marked " + title + " as sold to you. Use the chat to arrange collection or delivery."),
                    null, "Open the chat", links.conversation(r.conversationId()));
            case BOOK_AVAILABLE -> email(r, recipient, title + " is available again", "Back on the shelf",
                    List.of(title + ", which you asked about, is available again. It may not stay long."),
                    null, "Message the seller", links.conversation(r.conversationId()));
            case MARK_AS_SOLD_NUDGE -> email(r, recipient, "Did you sell " + title + "?", "Did you sell " + title + "?",
                    List.of("The chat about " + title + " has gone quiet. If you've sold it, mark it as sold so buyers don't keep asking about it."),
                    null, "Update my shelf", links.shelf());
            case STALE_LISTING -> email(r, recipient, "Give " + title + " a boost", "No enquiries for a while",
                    List.of(title + " hasn't had any enquiries in the last 30 days.",
                            "A clearer cover photo, a fuller description or a lower price can help."),
                    null, "Edit the listing", links.editBook(r.book()));
            case DRAFT_REMINDER -> email(r, recipient, "Finish listing " + title, "Your draft is waiting",
                    List.of("You started listing " + title + " but haven't published it yet. It only takes a minute to finish."),
                    null, "Finish my listing", links.editBook(r.book()));
            default -> throw new SkipDelivery("No email content for " + r.type());
        };
    }

    private EmailMessage email(NotificationRequest r, User recipient, String subject, String heading, List<String> lines,
                               String quote, String ctaLabel, String ctaUrl) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", firstName(recipient));
        vars.put("heading", heading);
        vars.put("lines", lines);
        vars.put("quote", quote);
        vars.put("ctaLabel", ctaLabel);
        vars.put("ctaUrl", ctaUrl);
        if (!r.type().mandatory()) {
            vars.put("unsubscribeUrl", links.unsubscribe(
                    unsubscribeTokens.create(recipient.getId(), r.type(), NotificationChannel.EMAIL)));
            vars.put("preferencesUrl", links.preferences());
        }
        return new EmailMessage(subject, "notification", vars);
    }

    private static String firstName(User user) {
        String full = user.getFullName();
        if (full == null || full.isBlank()) return "there";
        return full.trim().split("\\s+")[0];
    }
}
