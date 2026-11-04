package edu.bits.itsm.notification;

import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.repository.NotificationRepository;
import org.springframework.stereotype.Service;

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

    public void send(User recipient, NotificationFactory.Message msg, Long incidentId, String eventType,
                     NotificationChannelType... types) {
        if (recipient == null || !recipient.isActive()) return;
        for (NotificationChannelType t : types) {
            NotificationChannel ch = channels.get(t);
            if (ch == null) continue;
            if (t == NotificationChannelType.SMS && (recipient.getPhone() == null || recipient.getPhone().isBlank())) continue;
            Notification n = ch.compose(recipient, msg.subject(), msg.body(), incidentId, eventType);
            repository.save(n);
            ch.deliver(n);
        }
    }

    public List<NotificationChannelType> availableChannels() { return List.copyOf(channels.keySet()); }
}
