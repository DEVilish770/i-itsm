package edu.bits.itsm.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings bound from {@code itsm.ai.*} (env overrides {@code ITSM_AI_ENABLED}, {@code ITSM_AI_DAILY_LIMIT}).
 * The API key is deliberately not here: it is read only from the {@code ANTHROPIC_API_KEY} environment variable.
 *
 * @param enabled    start-up value of the on/off switch (an admin can flip it at runtime; not persisted)
 * @param model      Claude model id
 * @param dailyLimit calls allowed per day, counted from midnight Asia/Kolkata
 * @param timeouts   per-feature request timeouts
 */
@ConfigurationProperties(prefix = "itsm.ai")
public record AiProperties(@DefaultValue("true") boolean enabled,
                           @DefaultValue("claude-sonnet-5-5") String model,
                           @DefaultValue("150") int dailyLimit,
                           @DefaultValue Timeouts timeouts) {

    /** How long each feature may wait for Claude before it falls back to its rules answer. */
    public record Timeouts(@DefaultValue("8s") Duration triage,
                           @DefaultValue("8s") Duration chat,
                           @DefaultValue("8s") Duration search,
                           @DefaultValue("6s") Duration status,
                           @DefaultValue("8s") Duration summary) { }

    public Duration timeoutFor(AiFeature feature) {
        return switch (feature) {
            case SCREENSHOT_TRIAGE -> timeouts.triage();
            case CHAT -> timeouts.chat();
            case SEARCH -> timeouts.search();
            case STATUS_MESSAGE -> timeouts.status();
            case MANAGER_SUMMARY -> timeouts.summary();
        };
    }
}
