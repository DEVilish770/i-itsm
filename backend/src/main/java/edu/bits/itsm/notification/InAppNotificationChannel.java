package edu.bits.itsm.notification;

import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.event.AfterCommit;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** In-app notification: persisted (so it shows in the bell icon) and pushed live over WebSocket. */
@Component
public class InAppNotificationChannel implements NotificationChannel {

    private final SimpMessagingTemplate ws;

    public InAppNotificationChannel(SimpMessagingTemplate ws) { this.ws = ws; }

    @Override public NotificationChannelType type() { return NotificationChannelType.IN_APP; }

    @Override
    public Notification compose(User recipient, String subject, String body, Long incidentId, String eventType) {
        return new Notification(recipient.getId(), recipient.getUsername(), type(), subject, body, incidentId, eventType);
    }

    @Override
    public void deliver(Notification n) {
        String topic = "/topic/user/" + n.getRecipientId();
        Map<String, Object> payload = Map.of("subject", n.getSubject(), "body", n.getBody(), "incidentId", n.getIncidentId() == null ? 0 : n.getIncidentId(),
                "eventType", n.getEventType(), "sentAt", n.getSentAt().toString());
        // The bell re-fetches its list when this push arrives, and the new row is only visible once committed.
        AfterCommit.run(() -> ws.convertAndSend(topic, payload));
    }
}
