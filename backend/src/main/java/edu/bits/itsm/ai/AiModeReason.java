package edu.bits.itsm.ai;

/** Why the AI features are in {@link AiMode#RULES}. Listed in the order they are checked. */
public enum AiModeReason {
    /** {@code itsm.ai.enabled} is false, or an admin switched AI off at runtime. */
    DISABLED,
    /** The {@code ANTHROPIC_API_KEY} environment variable is missing or blank. */
    NO_KEY,
    /** Claude rejected the key (401/403); lasts until a restart or until an admin switches AI on again. */
    KEY_INVALID,
    /**
     * The account's credit balance is too low, or a spend limit set on it was reached; lasts until midnight
     * Asia/Kolkata or until an admin switches AI on again.
     */
    CREDITS_EXHAUSTED,
    /** Today's call budget ({@code itsm.ai.daily-limit}) is used up; resets at midnight Asia/Kolkata. */
    DAILY_LIMIT,
    /**
     * Two calls in a row failed with a rate limit, server error, timeout, network failure or 404; Claude is tried
     * again after a 60-second cool-down, or at once when an admin switches AI on again.
     */
    UNAVAILABLE
}
