package com.personal.rebooked.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Frontend origins allowed to open the chat socket. SockJS always sends credentials, so these must be exact
 * origins, never "*". Configured with ALLOWED_ORIGINS (comma-separated); the origin of CLIENT_URL is always added.
 */
@Component
public class AllowedOrigins {
    private final List<String> origins;

    public AllowedOrigins(@Value("${app.allowed-origins}") String configured,
                          @Value("${client.data-url:}") String clientUrl) {
        Set<String> set = new LinkedHashSet<>();
        Arrays.stream(configured.split(",")).map(AllowedOrigins::originOf).filter(o -> !o.isEmpty()).forEach(set::add);
        String client = originOf(clientUrl);
        if (!client.isEmpty()) set.add(client);
        this.origins = List.copyOf(set);
    }

    public List<String> list() {
        return origins;
    }

    /** "https://rebooked.ng/app/" -> "https://rebooked.ng". Patterns such as "https://*.rebooked.ng" pass through. */
    private static String originOf(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.contains("*")) return trimmed;
        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() == null || uri.getHost() == null) return "";
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
