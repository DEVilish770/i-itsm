package edu.bits.itsm.ai;

import com.fasterxml.jackson.databind.JsonNode;
import edu.bits.itsm.ai.gateway.AiFailure;
import edu.bits.itsm.ai.gateway.AiGateway;
import edu.bits.itsm.ai.gateway.AiRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

import static edu.bits.itsm.ai.AiFeature.SEARCH;
import static edu.bits.itsm.ai.AiTestSupport.MODEL;
import static org.junit.jupiter.api.Assertions.*;

/** TC-43 .. TC-51, TC-79 .. TC-81: the AI mode (LIVE or RULES, and why) and the guarded call path every feature goes through. */
class AiModeServiceTest {

    /** 23:00 in India on 15 Nov 2026: one hour before the daily reset. */
    private static final Instant LATE_EVENING_IST = ZonedDateTime.of(2026, 11, 15, 23, 0, 0, 0, AiModeService.IST).toInstant();

    private final FakeAiGateway gateway = new FakeAiGateway();
    private final MutableClock clock = new MutableClock(LATE_EVENING_IST);

    private AiModeService service(boolean enabled, int dailyLimit) {
        return new AiModeService(AiTestSupport.properties(enabled, dailyLimit), gateway, clock);
    }

    private static AiRequest request() { return AiTestSupport.request(SEARCH); }

    private static AiStatus rules(AiModeReason reason, int callsToday, int limit) {
        return new AiStatus(AiMode.RULES, reason, MODEL, callsToday, limit);
    }

    @Test @DisplayName("TC-43 LIVE with a key and AI on: the gateway's answer comes through and the call is counted")
    void live() {
        gateway.answer(SEARCH, "{\"q\":\"vpn\"}");
        AiModeService ai = service(true, 150);
        assertEquals(new AiStatus(AiMode.LIVE, null, MODEL, 0, 150), ai.status());
        assertEquals("vpn", ai.call(request()).orElseThrow().get("q").asText());
        assertEquals(1, ai.status().callsToday());
    }

    @Test @DisplayName("TC-44 NO_KEY: without ANTHROPIC_API_KEY nothing is sent, nothing is counted, the caller gets empty")
    void noKey() {
        gateway.credentials(false);
        AiModeService ai = service(true, 150);
        assertEquals(rules(AiModeReason.NO_KEY, 0, 150), ai.status());
        assertTrue(ai.call(request()).isEmpty());
        assertTrue(gateway.requests().isEmpty());
        assertEquals(0, ai.status().callsToday());
    }

    @Test @DisplayName("TC-45 DISABLED by the property or by the admin switch; switching on again is LIVE")
    void disabled() {
        AiModeService ai = service(false, 150);
        assertEquals(rules(AiModeReason.DISABLED, 0, 150), ai.status());
        assertTrue(ai.call(request()).isEmpty());
        assertEquals(AiMode.LIVE, ai.setEnabled(true).mode());
        assertEquals(rules(AiModeReason.DISABLED, 0, 150), ai.setEnabled(false));
        assertTrue(gateway.requests().isEmpty(), "no call is made while switched off");
    }

    @Test @DisplayName("TC-46 DAILY_LIMIT: after the limit-th call it is RULES until midnight Asia/Kolkata, when the count restarts")
    void dailyLimit() {
        gateway.answer(SEARCH, "{}");
        AiModeService ai = service(true, 2);
        assertTrue(ai.call(request()).isPresent());
        assertTrue(ai.call(request()).isPresent());
        assertEquals(rules(AiModeReason.DAILY_LIMIT, 2, 2), ai.status());
        assertTrue(ai.call(request()).isEmpty());
        assertEquals(2, gateway.requests().size(), "the third call never reaches Claude");
        clock.advance(Duration.ofMinutes(59));                       // 23:59 IST, same day
        assertEquals(AiModeReason.DAILY_LIMIT, ai.status().reason());
        clock.advance(Duration.ofMinutes(1));                        // 00:00 IST
        assertEquals(new AiStatus(AiMode.LIVE, null, MODEL, 0, 2), ai.status());
    }

    @Test @DisplayName("TC-47 CREDITS_EXHAUSTED: rules mode for the rest of the day, LIVE again at midnight IST")
    void creditsExhausted() {
        gateway.fail(SEARCH, AiFailure.CREDITS_EXHAUSTED);
        AiModeService ai = service(true, 150);
        assertTrue(ai.call(request()).isEmpty());
        assertEquals(rules(AiModeReason.CREDITS_EXHAUSTED, 1, 150), ai.status());
        clock.advance(Duration.ofMinutes(59));
        assertTrue(ai.call(request()).isEmpty());
        assertEquals(1, gateway.requests().size(), "no call is attempted while the credits are out");
        clock.advance(Duration.ofMinutes(1));
        assertEquals(AiMode.LIVE, ai.status().mode());
    }

    @Test @DisplayName("TC-48 KEY_INVALID (401/403) survives the day change; only an admin switching AI on again clears it")
    void keyInvalid() {
        gateway.fail(SEARCH, AiFailure.KEY_INVALID);
        AiModeService ai = service(true, 150);
        ai.call(request());
        assertEquals(AiModeReason.KEY_INVALID, ai.status().reason());
        clock.advance(Duration.ofDays(2));
        assertEquals(AiModeReason.KEY_INVALID, ai.status().reason());
        assertEquals(new AiStatus(AiMode.LIVE, null, MODEL, 0, 150), ai.setEnabled(true));
    }

    @Test @DisplayName("TC-49 UNAVAILABLE: two transient failures in a row (429, 5xx, timeout, network, 404) start a 60-second cool-down, then LIVE again")
    void unavailable() {
        gateway.fail(SEARCH, AiFailure.UNAVAILABLE);
        AiModeService ai = service(true, 150);
        ai.call(request());
        ai.call(request());
        assertEquals(AiModeReason.UNAVAILABLE, ai.status().reason());
        assertEquals(Duration.ofSeconds(60), AiModeService.COOL_DOWN);
        clock.advance(AiModeService.COOL_DOWN.minusSeconds(1));
        assertEquals(AiModeReason.UNAVAILABLE, ai.status().reason());
        assertTrue(ai.call(request()).isEmpty());
        assertEquals(2, gateway.requests().size(), "no call is attempted during the cool-down");
        clock.advance(Duration.ofSeconds(1));
        assertEquals(AiMode.LIVE, ai.status().mode());
    }

    @ParameterizedTest(name = "TC-50 {0} falls back for that request only; the mode stays LIVE")
    @EnumSource(value = AiFailure.class, names = {"REFUSED", "BAD_OUTPUT", "REJECTED"})
    void requestLevelFailure(AiFailure failure) {
        gateway.fail(SEARCH, failure);
        AiModeService ai = service(true, 150);
        assertTrue(ai.call(request()).isEmpty());
        assertEquals(new AiStatus(AiMode.LIVE, null, MODEL, 1, 150), ai.status());
    }

    @Test @DisplayName("TC-51 An unexpected exception from an adapter is caught: empty result, mode unchanged")
    void unexpectedException() {
        AiGateway broken = new AiGateway() {
            @Override public boolean hasCredentials() { return true; }
            @Override public JsonNode complete(AiRequest request) { throw new IllegalStateException("bug in an adapter"); }
        };
        AiModeService ai = new AiModeService(AiTestSupport.properties(true, 150), broken, clock);
        assertTrue(ai.call(request()).isEmpty());
        assertEquals(AiMode.LIVE, ai.status().mode());
    }

    @Test @DisplayName("TC-79 One transient failure falls back for that request only and the mode stays LIVE; any answer from Claude in between resets the count")
    void oneTransientFailureIsNotAnOutage() {
        AiModeService ai = service(true, 150);
        gateway.fail(SEARCH, AiFailure.UNAVAILABLE);
        assertTrue(ai.call(request()).isEmpty());
        assertEquals(new AiStatus(AiMode.LIVE, null, MODEL, 1, 150), ai.status(), "one slow or failed call does not turn every feature to rules");

        gateway.answer(SEARCH, "{}");
        assertTrue(ai.call(request()).isPresent());
        gateway.fail(SEARCH, AiFailure.UNAVAILABLE);
        ai.call(request());
        assertEquals(AiMode.LIVE, ai.status().mode(), "the answer in between started the count again");

        gateway.fail(SEARCH, AiFailure.REJECTED);
        ai.call(request());
        gateway.fail(SEARCH, AiFailure.UNAVAILABLE);
        ai.call(request());
        assertEquals(AiMode.LIVE, ai.status().mode(), "a 4xx is an answer from Claude too, so it also resets the count");

        ai.call(request());
        assertEquals(AiModeReason.UNAVAILABLE, ai.status().reason(), "two failures with nothing in between start the cool-down");
    }

    @Test @DisplayName("TC-80 After a cool-down the next call is a probe: if it fails again the cool-down restarts at once; once Claude answers, it takes two failures again")
    void probeAfterCoolDown() {
        AiModeService ai = service(true, 150);
        gateway.fail(SEARCH, AiFailure.UNAVAILABLE);
        ai.call(request());
        ai.call(request());
        clock.advance(AiModeService.COOL_DOWN);
        assertEquals(AiMode.LIVE, ai.status().mode());
        ai.call(request());
        assertEquals(AiModeReason.UNAVAILABLE, ai.status().reason(), "Claude is still down: one probe, then another cool-down");
        assertEquals(3, gateway.requests().size());

        clock.advance(AiModeService.COOL_DOWN);
        gateway.answer(SEARCH, "{}");
        assertTrue(ai.call(request()).isPresent());
        gateway.fail(SEARCH, AiFailure.UNAVAILABLE);
        ai.call(request());
        assertEquals(AiMode.LIVE, ai.status().mode());
    }

    @Test @DisplayName("TC-81 Switching AI on again clears CREDITS_EXHAUSTED and a running cool-down, so the next call checks again (e.g. after a credit top-up)")
    void switchingOnClearsCreditsAndCoolDown() {
        AiModeService ai = service(true, 150);
        gateway.fail(SEARCH, AiFailure.CREDITS_EXHAUSTED);
        ai.call(request());
        assertEquals(AiModeReason.CREDITS_EXHAUSTED, ai.status().reason());
        assertEquals(AiMode.LIVE, ai.setEnabled(true).mode(), "no wait until midnight IST, no restart");
        ai.call(request());
        assertEquals(AiModeReason.CREDITS_EXHAUSTED, ai.status().reason(), "still no credit: the next call finds it again");
        ai.setEnabled(true);
        gateway.answer(SEARCH, "{}");
        assertTrue(ai.call(request()).isPresent(), "topped up: Claude answers again");

        gateway.fail(SEARCH, AiFailure.UNAVAILABLE);
        ai.call(request());
        ai.call(request());
        assertEquals(AiModeReason.UNAVAILABLE, ai.status().reason());
        assertEquals(AiMode.LIVE, ai.setEnabled(true).mode(), "no need to wait out the cool-down");
        ai.call(request());
        assertEquals(AiMode.LIVE, ai.status().mode(), "the count was cleared too: one failure is not two in a row");
        assertEquals(6, gateway.requests().size());
    }
}
