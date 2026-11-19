package com.personal.rebooked.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Controller
public class AdminDashboardController implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/admin-dashboard-assets/**")
                .addResourceLocations("classpath:/static/admin-dashboard/");
    }

    @GetMapping({"/admin-dashboard", "/admin-dashboard/", "/admin-dashboard/{*path}"})
    public ResponseEntity<Resource> dashboard() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/admin-dashboard/index.html"));
    }
}