package com.finpilot.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Injected clock so time-dependent logic is testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
