package com.personal.rebooked.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.api-key}")
    private String cloudinaryApiKey;


    @Value("${cloudnairy.cloud-name}")
    private String cloudinaryCloudName;


    @Value("${cloudinary.api-secret}")
    private String cloudinaryApiSecret;

    @Bean
    public Cloudinary cloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudinaryCloudName);
        config.put("api_key", cloudinaryApiKey);
        config.put("api_secret", cloudinaryApiSecret);
        return new Cloudinary(config);
    }
}
