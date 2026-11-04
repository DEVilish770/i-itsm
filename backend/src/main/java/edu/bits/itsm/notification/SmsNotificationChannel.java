package edu.bits.itsm.notification;

import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Simulated SMS: short message, only if the user has a phone number. */
@Component
public class SmsNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationChannel.class);

    @Override public NotificationChannelType type() { return NotificationChannelType.SMS; }

    @Override
    public Notification compose(User recipient, String subject, String body, Long incidentId, String eventType) {
        String sms = subject.length() > 140 ? subject.substring(0, 137) + "..." : subject;
        return new Notification(recipient.getId(), recipient.getPhone(), type(), sms, sms, incidentId, eventType);
    }

    @Override
    public void deliver(Notification n) { log.info("[SMS → {}] {}", n.getRecipientAddress(), n.getSubject()); }
}
