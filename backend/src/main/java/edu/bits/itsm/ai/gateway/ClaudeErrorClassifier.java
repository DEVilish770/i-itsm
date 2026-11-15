package edu.bits.itsm.ai.gateway;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;

import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;

/**
 * Turns Claude API errors into {@link AiFailure} kinds. The core is a pure function of (HTTP status, error type,
 * message) so it can be unit-tested without the network. Messages it builds never contain request data or credentials.
 */
final class ClaudeErrorClassifier {

    private static final Set<String> TRANSIENT_TYPES = Set.of("rate_limit_error", "overloaded_error", "api_error", "timeout_error");

    private ClaudeErrorClassifier() { }

    static AiFailure classify(int status, String errorType, String message) {
        String type = lower(errorType);
        String text = lower(message);
        if (isCreditProblem(status, type, text)) return AiFailure.CREDITS_EXHAUSTED;
        if (status == 401 || status == 403 || type.equals("authentication_error") || type.equals("permission_error"))
            return AiFailure.KEY_INVALID;
        if (isTransient(status, type)) return AiFailure.UNAVAILABLE;
        // 404: the model id (or the endpoint) does not exist, so every later call gets the same answer. It counts like an
        // outage: two in a row start the cool-down, instead of each call quietly falling back while "AI live" shows.
        if (status == 404 || type.equals("not_found_error")) return AiFailure.UNAVAILABLE;
        return AiFailure.REJECTED;
    }

    /**
     * Out of credit: "Your credit balance is too low..." (a 400), a 402 billing error, or a 400 saying that a spend
     * limit set on the account has been reached ("You have reached your specified API usage limits...").
     */
    static boolean isCreditProblem(int status, String errorType, String message) {
        String text = lower(message);
        return status == 402 || lower(errorType).equals("billing_error")
                || (text.contains("credit balance") && (text.contains("too low") || text.contains("insufficient")))
                || (status == 400 && (text.contains("usage limit") || text.contains("spend limit") || text.contains("spending limit")));
    }

    /** Claude was busy or failing for a moment: 408, 429 (rate limit), 5xx (529 = overloaded) or a matching error type. */
    static boolean isTransient(int status, String errorType) {
        return status == 408 || status == 429 || status >= 500 || TRANSIENT_TYPES.contains(lower(errorType));
    }

    /**
     * Worth one more try: a transient answer ({@link #isTransient}) or a connection that failed. A 4xx such as a 404 is
     * not, because the same request would get the same answer. Whether enough time is left is the caller's check.
     */
    static boolean worthRetrying(RuntimeException ex) {
        if (ex instanceof AnthropicServiceException s) {
            String type = s.errorType().map(Object::toString).orElse("");
            return isTransient(s.statusCode(), type) && !isCreditProblem(s.statusCode(), type, describe(s));
        }
        return ex instanceof AnthropicIoException || ex instanceof AnthropicRetryableException || ex instanceof UncheckedIOException;
    }

    /** A 400 saying the server-side refusal fallback (its beta header or the {@code fallbacks} field) is not available. */
    static boolean rejectsServerFallback(String message) {
        String text = lower(message);
        return text.contains("server-side-fallback") || text.contains("fallbacks");
    }

    static AiGatewayException toGatewayException(RuntimeException ex) {
        if (ex instanceof AnthropicServiceException s) {
            String type = s.errorType().map(Object::toString).orElse("");
            AiFailure failure = classify(s.statusCode(), type, describe(s));
            return new AiGatewayException(failure, "Claude API answered HTTP " + s.statusCode() + (type.isEmpty() ? "" : " (" + type + ")"), ex);
        }
        if (ex instanceof AnthropicInvalidDataException)
            return new AiGatewayException(AiFailure.BAD_OUTPUT, "Claude's answer could not be read", ex);
        if (ex instanceof AnthropicException || ex instanceof UncheckedIOException)   // network failure or timeout
            return new AiGatewayException(AiFailure.UNAVAILABLE, "Claude could not be reached (" + ex.getClass().getSimpleName() + ")", ex);
        return new AiGatewayException(AiFailure.REJECTED, "Unexpected error while calling Claude (" + ex.getClass().getSimpleName() + ")", ex);
    }

    /** Message plus error body: the credit-balance sentence may sit in either. */
    static String describe(AnthropicServiceException s) {
        return s.getMessage() + " " + s.body();
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
