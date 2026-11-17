package com.personal.rebooked.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MailService against a fake Resend API on localhost. */
class MailServiceTest {
    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private int status = 200;
    private String response = "{\"id\":\"4ef9a417-02e9-4d39-ad75-9611e0fcc33c\"}";

    private MailService mailService;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/mail/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        mailService = new MailService(engine, "re_test_key", "Rebooked <hello@rebooked.test>",
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void postsRenderedTemplateToResend() throws Exception {
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Ada");
        vars.put("heading", "Welcome to Rebooked");
        vars.put("lines", List.of("Your email is confirmed."));
        vars.put("ctaLabel", "Set up your profile");
        vars.put("ctaUrl", "https://rebooked.test/welcome");

        mailService.send("ada@example.com", "Welcome to Rebooked, Ada!", "notification", vars);

        assertThat(path.get()).isEqualTo("POST /emails");
        assertThat(auth.get()).isEqualTo("Bearer re_test_key");
        JsonNode json = new ObjectMapper().readTree(body.get());
        assertThat(json.get("from").asText()).isEqualTo("Rebooked <hello@rebooked.test>");
        assertThat(json.get("to").get(0).asText()).isEqualTo("ada@example.com");
        assertThat(json.get("subject").asText()).isEqualTo("Welcome to Rebooked, Ada!");
        assertThat(json.get("html").asText())
                .contains("Welcome to Rebooked")
                .contains("https://rebooked.test/welcome");
    }

    @Test
    void rejectionCarriesResendsErrorMessage() {
        status = 403;
        response = "{\"statusCode\":403,\"name\":\"validation_error\",\"message\":\"The rebooked.test domain is not verified.\"}";

        assertThatThrownBy(() -> mailService.send("ada@example.com", "Your Rebooked verification code",
                "email_verification", Map.of("name", "Ada", "token", "123456")))
                .isInstanceOf(MailService.MailDeliveryException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("domain is not verified");
    }
}
