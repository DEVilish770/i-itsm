package edu.bits.itsm.notification;

import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.notification.TextSource;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sends one message to one user over the requested channels, persisting each delivery.
 * Looks channels up by type from the registry of beans — the Factory Method for channels.
 */
@Service
public class NotificationDispatcher {

    private final Map<NotificationChannelType, NotificationChannel> channels = new EnumMap<>(NotificationChannelType.class);
    private final NotificationRepository repository;

    public NotificationDispatcher(List<NotificationChannel> channelBeans, NotificationRepository repository) {
        channelBeans.forEach(c -> channels.put(c.type(), c));
        this.repository = repository;
    }

    /** @return the notifications saved (one per channel actually used), so a caller can refine them later */
    public List<Notification> send(User recipient, NotificationFactory.Message msg, Long incidentId, String eventType,
                                   NotificationChannelType... types) {
        List<Notification> saved = new ArrayList<>();
        if (recipient == null || !recipient.isActive()) return saved;
        for (NotificationChannelType t : types) {
            NotificationChannel ch = channels.get(t);
            if (ch == null) continue;
            if (t == NotificationChannelType.SMS && (recipient.getPhone() == null || recipient.getPhone().isBlank())) continue;
            Notification n = ch.compose(recipient, msg.subject(), msg.body(), incidentId, eventType);
            // Only the bell shows the plain-language line; an email or SMS carries the subject and body, so its stored
            // row must not claim a line (or an AI tag) that the delivered message never had.
            if (msg.plainText() != null && t == NotificationChannelType.IN_APP) n.describePlainly(msg.plainText(), TextSource.RULES);
            repository.save(n);
            ch.deliver(n);
            saved.add(n);
        }
        return saved;
    }

    public List<NotificationChannelType> availableChannels() { return List.copyOf(channels.keySet()); }
}
