package com.edusupport.ticket;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlaConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
