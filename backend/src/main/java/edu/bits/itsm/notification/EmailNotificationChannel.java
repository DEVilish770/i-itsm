package edu.bits.itsm.notification;

import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.user.User;
import org.springframework.stereotype.Component;

/** <b>Adapter</b>: adapts the generic NotificationChannel contract onto an EmailGateway. */
@Component
public class EmailNotificationChannel implements NotificationChannel {

    private final EmailGateway gateway;

    public EmailNotificationChannel(EmailGateway gateway) { this.gateway = gateway; }

    @Override public NotificationChannelType type() { return NotificationChannelType.EMAIL; }

    @Override
    public Notification compose(User recipient, String subject, String body, Long incidentId, String eventType) {
        return new Notification(recipient.getId(), recipient.getEmail(), type(), "[I-ITSM] " + subject, body, incidentId, eventType);
    }

    @Override
    public void deliver(Notification n) { gateway.sendEmail(n.getRecipientAddress(), n.getSubject(), n.getBody()); }
}
