package edu.bits.itsm.ai;

import edu.bits.itsm.ai.feature.LiveCatalog;
import edu.bits.itsm.ai.gateway.AiMessage;
import edu.bits.itsm.ai.gateway.AiRequest;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared fixtures for the AI tests. */
public final class AiTestSupport {

    public static final String MODEL = "claude-sonnet-5-5";
    /** A real 1x1 PNG. */
    public static final String PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    private AiTestSupport() { }

    public static AiProperties properties(boolean enabled, int dailyLimit) {
        return new AiProperties(enabled, MODEL, dailyLimit, new AiProperties.Timeouts(
                Duration.ofSeconds(8), Duration.ofSeconds(8), Duration.ofSeconds(8), Duration.ofSeconds(6), Duration.ofSeconds(8)));
    }

    /** A mode service in front of the fake, LIVE unless the fake is told otherwise. */
    public static AiModeService liveMode(FakeAiGateway gateway) {
        return new AiModeService(properties(true, 150), gateway, Clock.system(AiModeService.IST));
    }

    public static AiRequest request(AiFeature feature) {
        return new AiRequest(feature, "system", List.of(AiMessage.user("hello")), Map.of("type", "object"));
    }

    /** Two services, two categories and one technician, with ids as in the live catalog. */
    public static LiveCatalog.Snapshot catalog() {
        return new LiveCatalog.Snapshot(
                List.of(new LiveCatalog.Entry(1L, "Payment Application", "Customer payment & checkout"),
                        new LiveCatalog.Entry(4L, "VPN", "Remote access gateway")),
                List.of(new LiveCatalog.Entry(3L, "NETWORK", "Connectivity"),
                        new LiveCatalog.Entry(8L, "APPLICATION", "Business applications")),
                List.of(new LiveCatalog.Entry(7L, "Arjun Mehta", null)));
    }

    public static LiveCatalog catalogService() {
        LiveCatalog catalog = mock(LiveCatalog.class);
        when(catalog.snapshot()).thenReturn(catalog());
        return catalog;
    }
}
