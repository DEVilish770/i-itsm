package edu.bits.itsm.domain.notification;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A message delivered (or simulated as delivered) to a user over one channel. */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notif_recipient", columnList = "recipient_id"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "recipient_address", length = 160)
    private String recipientAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannelType channel;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "event_type", length = 40)
    private String eventType;

    @Column(nullable = false)
    private boolean read = false;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    protected Notification() { }

    public Notification(Long recipientId, String recipientAddress, NotificationChannelType channel,
                        String subject, String body, Long incidentId, String eventType) {
        this.recipientId = recipientId;
        this.recipientAddress = recipientAddress;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.incidentId = incidentId;
        this.eventType = eventType;
    }

    public void markRead() { this.read = true; }

    public Long getId() { return id; }
    public Long getRecipientId() { return recipientId; }
    public String getRecipientAddress() { return recipientAddress; }
    public NotificationChannelType getChannel() { return channel; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Long getIncidentId() { return incidentId; }
    public String getEventType() { return eventType; }
    public boolean isRead() { return read; }
    public LocalDateTime getSentAt() { return sentAt; }
}
