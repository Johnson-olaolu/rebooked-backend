package com.personal.rebooked.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Renders a Thymeleaf template from templates/mail/ and sends it through Resend (https://resend.com/docs/api-reference/emails/send-email).
 * Used by the notification EmailProvider; a thrown exception's message is what gets recorded on a FAILED delivery.
 */
@Service
public class MailService {
    private final TemplateEngine templateEngine;
    private final RestClient resend;
    private final String from;

    public MailService(TemplateEngine templateEngine,
                       @Value("${resend.api-key}") String apiKey,
                       @Value("${resend.from}") String from,
                       @Value("${resend.api-url:https://api.resend.com}") String apiUrl) {
        this.templateEngine = templateEngine;
        this.from = from;
        // Account emails are sent inside the request (see NotificationService), so don't let a slow Resend hang it
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.resend = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    public void send(String to, String subject, String template, Map<String, Object> variables) {
        Context context = new Context();
        if (variables != null) variables.forEach(context::setVariable);
        String html = templateEngine.process(template, context);

        try {
            resend.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", from, "to", List.of(to), "subject", subject, "html", html))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new MailDeliveryException("Resend rejected the email (" + e.getStatusCode().value() + "): "
                    + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new MailDeliveryException("Could not reach Resend: " + e.getMessage(), e);
        }
    }

    public static class MailDeliveryException extends RuntimeException {
        public MailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
