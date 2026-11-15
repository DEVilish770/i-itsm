package edu.bits.itsm.ai.gateway;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static edu.bits.itsm.ai.gateway.ClaudeErrorClassifier.*;
import static org.junit.jupiter.api.Assertions.*;

/** TC-52 .. TC-55, TC-82: Claude API errors become failure kinds, checked without the network. */
class ClaudeErrorClassifierTest {

    private static final String CREDIT_MESSAGE =
            "Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits.";

    private static JsonValue errorBody(String type, String message) {
        return JsonValue.from(Map.of("type", "error", "error", Map.of("type", type, "message", message)));
    }

    @Test @DisplayName("TC-52 Credit exhaustion is recognised from the message (a 400) or a 402 billing error; other 400s are not")
    void creditExhaustion() {
        assertEquals(AiFailure.CREDITS_EXHAUSTED, classify(400, "invalid_request_error", "400: " + CREDIT_MESSAGE));
        assertEquals(AiFailure.CREDITS_EXHAUSTED, classify(402, "billing_error", "Billing problem"));
        assertEquals(AiFailure.REJECTED, classify(400, "invalid_request_error", "messages: roles must alternate"));
        BadRequestException sdk = BadRequestException.builder().headers(Headers.builder().build())
                .body(errorBody("invalid_request_error", CREDIT_MESSAGE)).build();
        assertEquals(AiFailure.CREDITS_EXHAUSTED, toGatewayException(sdk).failure(), "detected from the SDK exception's body too");
    }

    @ParameterizedTest(name = "TC-53 HTTP {0} {1} -> {2}")
    @CsvSource({
            "401, authentication_error, KEY_INVALID",
            "403, permission_error,     KEY_INVALID",
            "429, rate_limit_error,     UNAVAILABLE",
            "500, api_error,            UNAVAILABLE",
            "529, overloaded_error,     UNAVAILABLE",
            "404, not_found_error,      UNAVAILABLE",
            "400, invalid_request_error, REJECTED",
            "413, request_too_large,    REJECTED"
    })
    void statusCodes(int status, String type, AiFailure expected) {
        assertEquals(expected, classify(status, type, "some message"));
    }

    @Test @DisplayName("TC-54 Typed SDK exceptions: 401 is KEY_INVALID, 429 and network errors or timeouts are UNAVAILABLE")
    void sdkExceptions() {
        Headers none = Headers.builder().build();
        assertEquals(AiFailure.KEY_INVALID, toGatewayException(UnauthorizedException.builder().headers(none)
                .body(errorBody("authentication_error", "invalid x-api-key")).build()).failure());
        assertEquals(AiFailure.UNAVAILABLE, toGatewayException(RateLimitException.builder().headers(none)
                .body(errorBody("rate_limit_error", "slow down")).build()).failure());
        assertEquals(AiFailure.UNAVAILABLE, toGatewayException(new AnthropicIoException("timeout")).failure());
        assertEquals(AiFailure.REJECTED, toGatewayException(new IllegalStateException("bug")).failure());
    }

    @Test @DisplayName("TC-55 A key that may not use the server-side refusal fallback is recognised, so the call is retried without it")
    void serverFallbackRejection() {
        assertTrue(rejectsServerFallback("Unexpected value(s) `server-side-fallback-2026-07-01` for the `anthropic-beta` header."));
        assertTrue(rejectsServerFallback("fallbacks: Extra inputs are not permitted"));
        assertFalse(rejectsServerFallback(CREDIT_MESSAGE));
    }

    @Test @DisplayName("TC-82 A spend or usage limit (a 400) is CREDITS_EXHAUSTED, a 404 for the model is UNAVAILABLE; only 408/429/5xx and dropped connections are worth a retry")
    void limitsNotFoundAndRetries() {
        Headers none = Headers.builder().build();
        String usageLimit = "You have reached your specified API usage limits. You will regain access on 2026-12-01 at 00:00 UTC.";
        assertEquals(AiFailure.CREDITS_EXHAUSTED, classify(400, "invalid_request_error", usageLimit));
        assertEquals(AiFailure.CREDITS_EXHAUSTED, classify(400, "invalid_request_error", "You have reached your specified workspace API usage limits."));
        assertEquals(AiFailure.CREDITS_EXHAUSTED, classify(400, "invalid_request_error", "This request would exceed your spend limit."));
        assertEquals(AiFailure.UNAVAILABLE, classify(429, "rate_limit_error", "Number of requests has exceeded your rate limit"),
                "a rate limit is not a spend limit");
        BadRequestException limitHit = BadRequestException.builder().headers(none).body(errorBody("invalid_request_error", usageLimit)).build();
        assertEquals(AiFailure.CREDITS_EXHAUSTED, toGatewayException(limitHit).failure());
        NotFoundException noSuchModel = NotFoundException.builder().headers(none).body(errorBody("not_found_error", "model: claude-sonnet-5-5")).build();
        assertEquals(AiFailure.UNAVAILABLE, toGatewayException(noSuchModel).failure());

        assertTrue(worthRetrying(InternalServerException.builder().statusCode(529).headers(none).body(errorBody("overloaded_error", "Overloaded")).build()));
        assertTrue(worthRetrying(RateLimitException.builder().headers(none).body(errorBody("rate_limit_error", "slow down")).build()));
        assertTrue(worthRetrying(new AnthropicIoException("Request failed", new java.net.ConnectException("Connection refused"))));
        assertFalse(worthRetrying(noSuchModel), "the same request gets the same 404");
        assertFalse(worthRetrying(limitHit));
        assertFalse(worthRetrying(BadRequestException.builder().headers(none).body(errorBody("invalid_request_error", "messages: roles must alternate")).build()));
        assertFalse(worthRetrying(new IllegalStateException("bug")));
    }
}
