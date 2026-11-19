package com.personal.rebooked.homepage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

@Configuration
public class HomepageConfig {

    /** "Now" for the weekly sections, so tests can pin it. */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Personalised AI picks (§24f) run the model call here, so the request can give up after 8 s
     * while the call finishes in the background. Small and bounded: extra work is refused, not queued.
     */
    @Bean
    public ThreadPoolTaskExecutor homepageAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("homepage-ai-");
        executor.initialize();
        return executor;
    }
}
