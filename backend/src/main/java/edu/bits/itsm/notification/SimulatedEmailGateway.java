package edu.bits.itsm.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Simulated SMTP provider. Logs the mail and keeps the last 200 in memory so the
 * admin UI can show an "outbox". Swap for a real SMTP/SendGrid adapter by providing
 * another {@link EmailGateway} bean — nothing else changes (Adapter pattern).
 */
@Component
public class SimulatedEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailGateway.class);
    private final Deque<String> outbox = new ArrayDeque<>();

    @Override
    public synchronized void sendEmail(String to, String subject, String body) {
        String line = "[EMAIL → " + to + "] " + subject + " :: " + body;
        log.info(line);
        outbox.addFirst(line);
        if (outbox.size() > 200) outbox.removeLast();
    }

    public synchronized List<String> recent() { return List.copyOf(outbox); }
}
