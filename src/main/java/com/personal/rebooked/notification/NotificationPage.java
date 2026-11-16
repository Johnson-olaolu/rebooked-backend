package com.personal.rebooked.notification;

import com.personal.rebooked.notification.models.Notification;

import java.util.List;

public record NotificationPage(List<Notification> content, int page, int pageSize, long totalElements,
                               int totalPages, long unreadTotal) {}
