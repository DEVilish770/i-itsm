package edu.bits.itsm.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Puts the {@link FakeAiGateway} in front of the real Claude adapter, so a test context can run LIVE without the network. */
@TestConfiguration
public class FakeAiGatewayConfig {

    @Bean
    @Primary
    public FakeAiGateway fakeAiGateway() { return new FakeAiGateway(); }
}
