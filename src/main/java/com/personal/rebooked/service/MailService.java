package com.personal.rebooked.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/** Renders a Thymeleaf template from templates/mail/ and sends it. Used by the notification EmailProvider. */
@RequiredArgsConstructor
@Service
public class MailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void send(String to, String subject, String template, Map<String, Object> variables) throws MessagingException {
        Context context = new Context();
        if (variables != null) variables.forEach(context::setVariable);
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(templateEngine.process(template, context), true);
        mailSender.send(mimeMessage);
    }
}
