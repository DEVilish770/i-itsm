package edu.bits.itsm.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bits.itsm.ai.gateway.AiFailure;
import edu.bits.itsm.ai.gateway.AiGateway;
import edu.bits.itsm.ai.gateway.AiGatewayException;
import edu.bits.itsm.ai.gateway.AiRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test double for the {@link AiGateway} port: answers with canned JSON or fails with a chosen kind, per feature, and
 * records every request it receives. A feature can be held so its calls wait until {@link #release()} (async tests).
 * It never touches the network.
 */
public class FakeAiGateway implements AiGateway {

    private final ObjectMapper json = new ObjectMapper();
    private final Map<AiFeature, String> answers = new ConcurrentHashMap<>();
    private final Map<AiFeature, AiFailure> failures = new ConcurrentHashMap<>();
    private final Map<AiFeature, CountDownLatch> holds = new ConcurrentHashMap<>();
    private final List<AiRequest> requests = new CopyOnWriteArrayList<>();
    private volatile boolean credentials = true;

    public FakeAiGateway answer(AiFeature feature, String jsonAnswer) {
        failures.remove(feature);
        answers.put(feature, jsonAnswer);
        return this;
    }

    public FakeAiGateway fail(AiFeature feature, AiFailure failure) {
        failures.put(feature, failure);
        return this;
    }

    public void credentials(boolean present) { credentials = present; }

    /** Calls for this feature block until {@link #release()} (at most 10 s). */
    public void hold(AiFeature feature) { holds.put(feature, new CountDownLatch(1)); }

    public void release() { holds.values().forEach(CountDownLatch::countDown); holds.clear(); }

    public List<AiRequest> requests() { return List.copyOf(requests); }

    public List<AiRequest> requests(AiFeature feature) { return requests.stream().filter(r -> r.feature() == feature).toList(); }

    public void reset() {
        release();
        answers.clear();
        failures.clear();
        requests.clear();
        credentials = true;
    }

    @Override
    public boolean hasCredentials() { return credentials; }

    @Override
    public JsonNode complete(AiRequest request) {
        requests.add(request);
        CountDownLatch hold = holds.get(request.feature());
        if (hold != null) await(hold);
        AiFailure failure = failures.get(request.feature());
        if (failure != null) throw new AiGatewayException(failure, "fake " + failure);
        String answer = answers.get(request.feature());
        if (answer == null) throw new AiGatewayException(AiFailure.BAD_OUTPUT, "no canned answer for " + request.feature());
        try {
            return json.readTree(answer);
        } catch (JsonProcessingException ex) {
            throw new AiGatewayException(AiFailure.BAD_OUTPUT, "canned answer is not JSON", ex);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
