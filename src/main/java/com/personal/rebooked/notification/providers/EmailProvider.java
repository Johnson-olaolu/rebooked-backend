package com.personal.rebooked.notification.providers;

import com.personal.rebooked.notification.NotificationChannel;
import com.personal.rebooked.notification.NotificationProvider;
import com.personal.rebooked.notification.NotificationRequest;
import com.personal.rebooked.notification.NotificationType;
import com.personal.rebooked.service.MailService;
import com.personal.rebooked.user.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Email through MailService (Resend). Only verified addresses get anything but the account emails. */
@Component
@RequiredArgsConstructor
public class EmailProvider implements NotificationProvider {
    private final MailService mailService;
    private final EmailContent content;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean supports(NotificationType type) {
        return content.supports(type);
    }

    @Override
    public void deliver(NotificationRequest request, User recipient) throws Exception {
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            throw new SkipDelivery("No email address");
        }
        if (!request.type().mandatory() && !recipient.isEmailVerified()) {
            throw new SkipDelivery("Email not verified");
        }
        EmailMessage message = content.render(request, recipient);
        mailService.send(recipient.getEmail(), message.subject(), message.template(), message.variables());
    }
}
