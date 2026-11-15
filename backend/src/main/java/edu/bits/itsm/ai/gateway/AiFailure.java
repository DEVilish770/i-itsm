package edu.bits.itsm.ai.gateway;

/** Why a gateway call produced no usable answer. The mode service turns the first three into a mode change. */
public enum AiFailure {
    /** 401/403: the key is wrong or lacks permission. */
    KEY_INVALID,
    /** The credit balance is too low, a spend limit set on the account was reached, or another billing error. */
    CREDITS_EXHAUSTED,
    /** 408, 429, 5xx, timeout or network error (the gateway has already tried once more if time allowed), or a 404 such as an unknown model. */
    UNAVAILABLE,
    /** {@code stop_reason == refusal}, even after the server-side fallback: only this request falls back. */
    REFUSED,
    /** The answer was cut off or was not the expected JSON: only this request falls back. */
    BAD_OUTPUT,
    /** Any other 4xx (for example an invalid request): only this request falls back. */
    REJECTED
}
