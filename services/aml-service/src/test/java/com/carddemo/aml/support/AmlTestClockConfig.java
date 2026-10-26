package com.carddemo.aml.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/**
 * 用可推进时钟替换生产 {@link Clock}。
 */
@TestConfiguration
public class AmlTestClockConfig {

    public static final String BASE_INSTANT = "2026-10-26T04:00:00Z";
    public static final MutableClock CLOCK = MutableClock.at(BASE_INSTANT);

    @Bean
    @Primary
    public Clock amlTestClock() {
        return CLOCK;
    }
}
