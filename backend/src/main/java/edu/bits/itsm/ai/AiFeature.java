package edu.bits.itsm.ai;

/** The five Claude-powered features. Each has its own timeout and a deterministic rules fallback. */
public enum AiFeature {
    SCREENSHOT_TRIAGE, CHAT, SEARCH, STATUS_MESSAGE, MANAGER_SUMMARY
}
