package com.personal.rebooked.service;

import com.personal.rebooked.user.models.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.logging.Logger;

@RequiredArgsConstructor
@Service
public class MailService {
    @Value("${client.data-url}")
    String clientUrl ;

    private final Logger logger =  Logger.getLogger(MailService.class.getName());
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    private void sendMail(String to, String subject, String template, Context context) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

        // Process the template with the given context
        String htmlContent = templateEngine.process(template, context);

        // Set email properties
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // Set true for HTML content

        // Send the email
        mailSender.send(mimeMessage);
    }

//    @Async
    public void sendConfirmEmailMail (User user) {
        Context context = new Context();
        context.setVariable("name", user.getFullName());
        context.setVariable("token", user.getConfirmEmailToken());

        try {
            sendMail(user.getEmail(), "Welcome to Rebooked Please confirm your email","email_verification", context);
            logger.info(String.format("Welcome email sent to %s created", user.getEmail()));
        }catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    public void sendChangepasswordMail (User user) {
        Context context = new Context();
        context.setVariable("name", user.getFullName());
        context.setVariable("url", String.format("%s/auth/change-password?email=%s&token=%s", clientUrl, user.getEmail(), user.getChangePasswordToken()));
        try {
            sendMail(user.getEmail(), "Forgot Password","forgot_password", context);
            logger.info(String.format("Forgot password email sent to %s created", user.getEmail()));
        }catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
