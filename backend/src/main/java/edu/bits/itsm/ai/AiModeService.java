package edu.bits.itsm.ai;

import com.fasterxml.jackson.databind.JsonNode;
import edu.bits.itsm.ai.gateway.AiFailure;
import edu.bits.itsm.ai.gateway.AiGateway;
import edu.bits.itsm.ai.gateway.AiGatewayException;
import edu.bits.itsm.ai.gateway.AiRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Decides whether the AI features run LIVE (Claude is asked) or in RULES mode, and why. Every gateway call goes
 * through {@link #call}: it checks the mode, counts the call, catches every error, flips the mode when the error says
 * so, and returns empty so the caller uses its rules answer. A circuit breaker in the shape of a small state machine:
 * <pre>
 *   DISABLED          property false or admin switch off               until the admin switches on
 *   NO_KEY            ANTHROPIC_API_KEY missing or blank                 until restart with a key
 *   KEY_INVALID       401/403                                            until restart or the admin switches on again
 *   CREDITS_EXHAUSTED credit balance too low, or a spend limit reached   until midnight Asia/Kolkata or the admin switches on again
 *   DAILY_LIMIT       calls today reached itsm.ai.daily-limit            until midnight Asia/Kolkata
 *   UNAVAILABLE       2 in a row: 429, 5xx, timeout, network error, 404  for a 60-second cool-down or until the admin switches on again
 * </pre>
 * A single transient failure only makes that request fall back (the gateway has already tried it once more if time
 * allowed), and any answer from Claude resets the count. When a cool-down ends, the next call is a probe: if it fails
 * the same way, the cool-down starts again at once. Refusals and malformed answers fall back for that one request
 * only. All state is in memory (not persisted).
 */
@Service
public class AiModeService {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final Duration COOL_DOWN = Duration.ofSeconds(60);
    /** Transient failures in a row (no answer from Claude in between) that start the cool-down. */
    static final int FAILURES_BEFORE_COOL_DOWN = 2;

    private static final Logger log = LoggerFactory.getLogger(AiModeService.class);

    private final AiProperties properties;
    private final AiGateway gateway;
    private final Clock clock;
    private final DailyCallCounter calls;

    // guarded by "this"
    private boolean enabled;
    private boolean keyInvalid;
    private LocalDate creditsOutOn;
    private Instant unavailableUntil;
    private int transientFailuresInARow;

    public AiModeService(AiProperties properties, AiGateway gateway, Clock clock) {
        this.properties = properties;
        this.gateway = gateway;
        this.clock = clock;
        this.calls = new DailyCallCounter(clock, IST);
        this.enabled = properties.enabled();
    }

    public synchronized AiStatus status() {
        AiModeReason reason = blockingReason();
        return new AiStatus(reason == null ? AiMode.LIVE : AiMode.RULES, reason, properties.model(), calls.today(), properties.dailyLimit());
    }

    public boolean isLive() { return status().mode() == AiMode.LIVE; }

    /**
     * Admin runtime switch (not persisted). Switching on also forgets a rejected key, used-up credits and a running
     * cool-down, so the next call checks them again (after a credit top-up, say); if one still holds, that call finds
     * it again and the reason comes back.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public synchronized AiStatus setEnabled(boolean on) {
        enabled = on;
        if (on) {
            keyInvalid = false;
            creditsOutOn = null;
            unavailableUntil = null;
            transientFailuresInARow = 0;
        }
        AiStatus s = status();
        log.info("AI switched {} by an administrator: now {}{}", on ? "on" : "off", s.mode(), s.reason() == null ? "" : " (" + s.reason() + ")");
        return s;
    }

    /** Asks the gateway when LIVE. Empty means "use your rules answer": RULES mode, or this call failed. */
    public Optional<JsonNode> call(AiRequest request) {
        if (!tryStartCall()) return Optional.empty();
        try {
            JsonNode answer = gateway.complete(request);
            recordAnswer();
            return Optional.of(answer);
        } catch (AiGatewayException ex) {
            recordFailure(request.feature(), ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("AI {} call failed unexpectedly: {}", request.feature(), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupMode() {
        AiStatus s = status();
        log.info("Claude AI features start in {} mode{}: model {}, daily limit {} calls", s.mode(),
                s.reason() == null ? "" : " (" + s.reason() + ")", s.model(), s.dailyLimit());
    }

    private synchronized boolean tryStartCall() {
        if (blockingReason() != null) return false;
        calls.increment();
        return true;
    }

    private synchronized void recordAnswer() { transientFailuresInARow = 0; }

    private synchronized void recordFailure(AiFeature feature, AiGatewayException ex) {
        // "In a row" means no answer from Claude in between: any reply, even a 4xx or a refusal, resets the count.
        transientFailuresInARow = ex.failure() == AiFailure.UNAVAILABLE ? transientFailuresInARow + 1 : 0;
        switch (ex.failure()) {
            case KEY_INVALID -> keyInvalid = true;
            case CREDITS_EXHAUSTED -> creditsOutOn = today();
            case UNAVAILABLE -> {
                if (transientFailuresInARow >= FAILURES_BEFORE_COOL_DOWN) unavailableUntil = clock.instant().plus(COOL_DOWN);
            }
            default -> { /* REFUSED, BAD_OUTPUT, REJECTED: only this request falls back */ }
        }
        AiModeReason reason = blockingReason();
        log.warn("AI {} call failed ({}): {}{}", feature, ex.failure(), ex.getMessage(),
                reason == null ? "" : "; rules mode now (" + reason + ")");
    }

    private AiModeReason blockingReason() {
        if (!enabled) return AiModeReason.DISABLED;
        if (!gateway.hasCredentials()) return AiModeReason.NO_KEY;
        if (keyInvalid) return AiModeReason.KEY_INVALID;
        if (creditsOutOn != null && !today().isAfter(creditsOutOn)) return AiModeReason.CREDITS_EXHAUSTED;
        if (calls.today() >= properties.dailyLimit()) return AiModeReason.DAILY_LIMIT;
        if (unavailableUntil != null && clock.instant().isBefore(unavailableUntil)) return AiModeReason.UNAVAILABLE;
        return null;
    }

    private LocalDate today() { return LocalDate.ofInstant(clock.instant(), IST); }
}
