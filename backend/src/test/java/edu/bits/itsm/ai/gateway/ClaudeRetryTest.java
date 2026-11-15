package edu.bits.itsm.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiProperties;
import edu.bits.itsm.ai.AiTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC-83, TC-84: the Claude adapter's single retry, through the real SDK against a fake Messages API on 127.0.0.1.
 * The key is fake and the base URL is local, so nothing here can reach Anthropic or spend credit.
 */
class ClaudeRetryTest {

    private static final String ANSWER = """
            {"id":"msg_test","type":"message","role":"assistant","model":"claude-sonnet-5-5",
             "content":[{"type":"text","text":"{\\"q\\":\\"vpn\\"}"}],
             "stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":6}}""";
    private static final Duration PAUSE = Duration.ofMillis(50);

    /** One scripted reply: an HTTP status and body after a delay, or (status 0) a connection closed with no answer. */
    private record Reply(int status, String body, long delayMillis) {
        static Reply ok() { return new Reply(200, ANSWER, 0); }
        static Reply error(int status, String type, String message) {
            return new Reply(status, "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message + "\"}}", 0);
        }
        static Reply dropped() { return new Reply(0, "", 0); }
        Reply after(long millis) { return new Reply(status, body, millis); }
    }

    private final Queue<Reply> script = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final ExecutorService handlers = Executors.newCachedThreadPool();
    private HttpServer server;

    @BeforeEach
    void startFakeApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(handlers);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopFakeApi() {
        server.stop(0);
        handlers.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        Reply reply = script.poll();
        if (reply == null) reply = Reply.ok();
        try {
            Thread.sleep(reply.delayMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (reply.status() == 0) { exchange.close(); return; }   // no status line: the client sees a dropped connection
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private ClaudeAiGateway gateway(Duration budget) {
        AiProperties properties = new AiProperties(true, AiTestSupport.MODEL, 150, new AiProperties.Timeouts(budget, budget, budget, budget, budget));
        String local = "http://127.0.0.1:" + server.getAddress().getPort();
        return new ClaudeAiGateway(properties, new ObjectMapper(), "test-key-not-real", local, PAUSE);
    }

    private static AiRequest request() { return AiTestSupport.request(AiFeature.SEARCH); }

    @ParameterizedTest(name = "TC-83 One more try after {0}: the second answer is used, and there is no third request")
    @ValueSource(strings = {"429 rate limit", "500 server error", "529 overloaded", "a dropped connection"})
    void retriedOnce(String first) {
        script.add(switch (first) {
            case "429 rate limit" -> Reply.error(429, "rate_limit_error", "Number of requests has exceeded your rate limit");
            case "500 server error" -> Reply.error(500, "api_error", "Internal server error");
            case "529 overloaded" -> Reply.error(529, "overloaded_error", "Overloaded");
            default -> Reply.dropped();
        });
        assertEquals("vpn", gateway(Duration.ofSeconds(6)).complete(request()).get("q").asText());
        assertEquals(2, requests.get());
    }

    @Test @DisplayName("TC-84 No second try after a 400, a 404, a spend limit or a timeout, nor when less than half the time budget is left; never a third try")
    void notRetried() {
        ClaudeAiGateway claude = gateway(Duration.ofSeconds(3));
        assertFailsAfter(1, AiFailure.REJECTED, claude, Reply.error(400, "invalid_request_error", "messages: roles must alternate"));
        assertFailsAfter(1, AiFailure.UNAVAILABLE, claude, Reply.error(404, "not_found_error", "model: claude-sonnet-5-5"));
        assertFailsAfter(1, AiFailure.CREDITS_EXHAUSTED, claude, Reply.error(400, "invalid_request_error", "You have reached your specified API usage limits."));
        assertFailsAfter(2, AiFailure.UNAVAILABLE, claude, Reply.error(529, "overloaded_error", "Overloaded"), Reply.error(529, "overloaded_error", "Overloaded"));
        assertFailsAfter(1, AiFailure.UNAVAILABLE, claude, Reply.error(529, "overloaded_error", "Overloaded").after(1700));

        ClaudeAiGateway hurried = gateway(Duration.ofMillis(600));
        long start = System.nanoTime();
        assertFailsAfter(1, AiFailure.UNAVAILABLE, hurried, Reply.ok().after(2500));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMillis(2000)) < 0,
                "the call gave up at its budget instead of waiting for the slow answer");
    }

    private void assertFailsAfter(int expectedRequests, AiFailure expected, ClaudeAiGateway claude, Reply... replies) {
        requests.set(0);
        script.clear();
        script.addAll(List.of(replies));
        AiGatewayException ex = assertThrows(AiGatewayException.class, () -> claude.complete(request()));
        assertEquals(expected, ex.failure());
        assertEquals(expectedRequests, requests.get(), "requests sent for " + expected);
        script.clear();
    }
}
