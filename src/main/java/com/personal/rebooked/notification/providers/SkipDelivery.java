package com.personal.rebooked.notification.providers;

/** Thrown by a provider that decides not to deliver (recorded as SKIPPED, not FAILED). */
public class SkipDelivery extends RuntimeException {
    public SkipDelivery(String reason) {
        super(reason);
    }
}
