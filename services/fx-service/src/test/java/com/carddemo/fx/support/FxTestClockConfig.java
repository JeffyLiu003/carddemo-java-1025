package com.carddemo.fx.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/**
 * 用可推进时钟替换生产 {@link Clock}，便于验证报价过期。
 */
@TestConfiguration
public class FxTestClockConfig {

    public static final String BASE_INSTANT = "2026-10-26T04:00:00Z";
    public static final MutableClock CLOCK = MutableClock.at(BASE_INSTANT);

    @Bean
    @Primary
    public Clock fxTestClock() {
        return CLOCK;
    }
}
