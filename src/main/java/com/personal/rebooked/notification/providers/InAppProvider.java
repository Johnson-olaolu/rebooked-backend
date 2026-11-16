package com.personal.rebooked.notification.providers;

import com.personal.rebooked.notification.InAppNotifications;
import com.personal.rebooked.notification.NotificationChannel;
import com.personal.rebooked.notification.NotificationProvider;
import com.personal.rebooked.notification.NotificationRequest;
import com.personal.rebooked.user.models.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The notifications page and bell: stored in Mongo and pushed over the user's socket. */
@Component
@RequiredArgsConstructor
public class InAppProvider implements NotificationProvider {
    private final InAppNotifications inApp;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void deliver(NotificationRequest request, User recipient) {
        inApp.create(request);
    }
}
