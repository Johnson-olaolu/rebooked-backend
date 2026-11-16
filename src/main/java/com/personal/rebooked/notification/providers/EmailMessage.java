package com.personal.rebooked.notification.providers;

import java.util.Map;

/** A rendered email: the subject, the Thymeleaf template under templates/mail/, and its variables. */
public record EmailMessage(String subject, String template, Map<String, Object> variables) {}
