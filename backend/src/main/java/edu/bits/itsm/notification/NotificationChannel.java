package edu.bits.itsm.notification;

import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.user.User;

/**
 * Contract for a delivery mechanism (Strategy-style interface; implementations are swapped by type).
 * Adding Slack/Teams/push later = one new class implementing this, zero changes elsewhere.
 */
public interface NotificationChannel {
    NotificationChannelType type();
    /** Build the channel-specific message for this user. */
    Notification compose(User recipient, String subject, String body, Long incidentId, String eventType);
    /** Deliver (real or simulated). Must not throw for simulated channels. */
    void deliver(Notification notification);
}
