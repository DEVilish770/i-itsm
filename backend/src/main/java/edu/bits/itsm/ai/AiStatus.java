package edu.bits.itsm.ai;

/** What the AI features are doing right now; the JSON body of {@code GET /api/ai/status}. {@code reason} is null when LIVE. */
public record AiStatus(AiMode mode, AiModeReason reason, String model, int callsToday, int dailyLimit) { }
