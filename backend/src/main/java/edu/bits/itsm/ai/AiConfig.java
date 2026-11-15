package edu.bits.itsm.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the AI module: binds {@link AiProperties} and supplies the clock used for the daily limit and cool-downs. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public Clock aiClock() { return Clock.system(AiModeService.IST); }
}
