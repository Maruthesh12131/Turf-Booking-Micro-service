package com.turfbooking.turf_service.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Slot dates and times are wall-clock times at the turf, so one fixed zone is used for "today" and "has it started".
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
