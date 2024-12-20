package com.personal.rebooked.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class FacebookVerificationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String FACEBOOK_GRAPH_URL = "https://graph.facebook.com/me?fields=id,name,email,picture&access_token=";

    public Map<String, Object> verifyToken(String accessToken) {
        try {
            String url = FACEBOOK_GRAPH_URL + accessToken;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || response.containsKey("error")) {
                throw new IllegalArgumentException("Invalid Facebook token");
            }
            return response;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to verify Facebook token", e);
        }
    }
}
