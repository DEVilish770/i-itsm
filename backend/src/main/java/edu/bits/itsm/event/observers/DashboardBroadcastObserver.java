package edu.bits.itsm.event.observers;

import edu.bits.itsm.event.AfterCommit;
import edu.bits.itsm.event.IncidentEvent;
import edu.bits.itsm.event.IncidentObserver;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Pushes every event to the live operations dashboard over STOMP/WebSocket (innovation: real-time dashboard). */
@Component
@Order(3)
public class DashboardBroadcastObserver implements IncidentObserver {

    private final SimpMessagingTemplate ws;

    public DashboardBroadcastObserver(SimpMessagingTemplate ws) { this.ws = ws; }

    @Override
    public void onIncidentEvent(IncidentEvent e) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", e.type().name());
        payload.put("incidentId", e.incident().getId());
        payload.put("incidentNumber", e.incident().getIncidentNumber());
        payload.put("title", e.incident().getTitle());
        payload.put("status", e.incident().getStatus().name());
        payload.put("priority", e.incident().getPriority() == null ? null : e.incident().getPriority().name());
        payload.put("slaStatus", e.incident().getSla().getStatus().name());
        payload.put("actor", e.actor());
        payload.put("description", e.description());
        payload.put("at", e.occurredAt().toString());
        // After commit, so the dashboard's re-fetch of its counters sees this change.
        AfterCommit.run(() -> ws.convertAndSend("/topic/incidents", payload));
    }
}
