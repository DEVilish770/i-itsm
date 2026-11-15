package edu.bits.itsm.notification;

import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.event.AfterCommit;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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

    /** Also used again when Claude's plain-language text replaces the template, so the bell reloads with the new words. */
    @Override
    public void deliver(Notification n) {
        String topic = "/topic/user/" + n.getRecipientId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", n.getId());
        payload.put("subject", n.getSubject());
        payload.put("body", n.getBody());
        payload.put("incidentId", n.getIncidentId() == null ? 0 : n.getIncidentId());
        payload.put("eventType", n.getEventType());
        payload.put("sentAt", n.getSentAt().toString());
        payload.put("plainText", n.getPlainText());
        payload.put("textSource", n.getTextSource() == null ? null : n.getTextSource().name());
        // The bell re-fetches its list when this push arrives, and the new row is only visible once committed.
        AfterCommit.run(() -> ws.convertAndSend(topic, payload));
    }
}
