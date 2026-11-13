package com.example.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * General application beans.
 */
@Configuration
public class AppConfig {

    /**
     * System clock used for "today". Injected into services so unit tests can fix the date.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
