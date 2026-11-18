package com.personal.rebooked.security;

import com.personal.rebooked.utils.Constants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.rebooked.auth.jwt.JwtFilter;
import com.personal.rebooked.config.AllowedOrigins;
import com.personal.rebooked.utils.ResponseHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationProvider authenticationProvider;
    private final JwtFilter jwtFilter;
    private final ObjectMapper objectMapper;
    private final AllowedOrigins allowedOrigins;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));

        // SockJS sends credentials, so the socket needs exact origins rather than "*".
        // Registered first: the first matching pattern wins.
        CorsConfiguration socket = new CorsConfiguration();
        socket.setAllowedOriginPatterns(allowedOrigins.list());
        socket.setAllowedMethods(List.of("*"));
        socket.setAllowedHeaders(List.of("*"));
        socket.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/ws/**", socket);
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((authorizeRequests) -> {
                    authorizeRequests
                            .requestMatchers("/").permitAll()
                            .requestMatchers("/error").permitAll()
                            // The admin area (authors, imports): superAdmin only; first so nothing below can open it
                            .requestMatchers("/admin/**").hasAuthority(Constants.ROLE_SUPER_ADMIN)
                            // Settings → Password needs the session; must win over /auth/** below
                            .requestMatchers(HttpMethod.POST, "/auth/change-password").authenticated()
                            .requestMatchers("/auth/**").permitAll()
                            // The socket authenticates on STOMP CONNECT (StompAuthInterceptor)
                            .requestMatchers("/ws/**").permitAll()
                            // The signed token in the email link is the credential
                            .requestMatchers(HttpMethod.POST, "/notification/unsubscribe").permitAll()
                            // Only public profiles are open; /user/me must win over the GET /user/* rule below
                            .requestMatchers("/user/me", "/user/me/**").authenticated()
                            .requestMatchers(HttpMethod.GET, "/user/*").permitAll()
                            .requestMatchers(HttpMethod.GET, "/user/*/ratings").permitAll()
                            .requestMatchers("/marketing/**").permitAll()
                            // Authors: reading is public; adding a missing author needs a login
                            .requestMatchers(HttpMethod.POST, "/author/proposals").authenticated()
                            .requestMatchers(HttpMethod.GET, "/author/**").permitAll()
                            .requestMatchers("/book/me").authenticated()
                            // Per-user lists; must win over the public GET /book/* rule below
                            .requestMatchers("/book/saved", "/book/saved/**", "/book/recently-viewed").authenticated()
                            .requestMatchers("/book/*/view").permitAll()
                            .requestMatchers("/book/ai-picks").permitAll()
                            .requestMatchers("/book/most-viewed").permitAll()
                            .requestMatchers(HttpMethod.GET, "/category", "/category/top").permitAll()
                            .requestMatchers(HttpMethod.POST, "/category", "/category/**").hasAuthority(Constants.ROLE_SUPER_ADMIN)
                            .requestMatchers(HttpMethod.PATCH, "/category/**").hasAuthority(Constants.ROLE_SUPER_ADMIN)
                            .requestMatchers(HttpMethod.DELETE, "/category/**").hasAuthority(Constants.ROLE_SUPER_ADMIN)
                            // Shop browsing is public; the literal routes above/below stay as configured
                            .requestMatchers(HttpMethod.GET, "/book/query").permitAll()
                            .requestMatchers(HttpMethod.GET, "/book/sold").authenticated()
                            .requestMatchers(HttpMethod.GET, "/book/*", "/book/slug/*").permitAll()
                            .requestMatchers("/documentation").permitAll()
                            .requestMatchers("/swagger-ui/**").permitAll()
                            .requestMatchers("/v3/api-docs/**").permitAll()
                            .anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpStatus.FORBIDDEN, "Forbidden")))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ResponseHandler.errorBody(status, message, null));
    }
}
