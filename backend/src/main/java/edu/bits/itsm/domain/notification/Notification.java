package edu.bits.itsm.domain.notification;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/** A message delivered (or simulated as delivered) to a user over one channel. */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notif_recipient", columnList = "recipient_id"))
public class Notification {

    /** Longest plain-language line stored; longer text is cut with an ellipsis. */
    public static final int PLAIN_TEXT_MAX = 600;

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

    /** Optional reporter-facing version of the message in everyday words; null when the event has none. */
    @Column(name = "plain_text", length = PLAIN_TEXT_MAX)
    private String plainText;

    /** Who wrote {@link #plainText}: a template (RULES) at first, possibly replaced by Claude's words (AI) shortly after. */
    @Enumerated(EnumType.STRING)
    @Column(name = "text_source", length = 10)
    private TextSource textSource;

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

    /** Sets the plain-language line and who wrote it; blank text clears both, long text is cut to fit the column. */
    public void describePlainly(String text, TextSource source) {
        this.plainText = fitPlainText(text);
        this.textSource = plainText == null ? null : Objects.requireNonNull(source, "source");
    }

    /** The plain-language line as stored: trimmed and cut to {@link #PLAIN_TEXT_MAX} with an ellipsis; null when blank. */
    public static String fitPlainText(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.strip();
        return t.length() > PLAIN_TEXT_MAX ? t.substring(0, PLAIN_TEXT_MAX - 3) + "..." : t;
    }

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
    public String getPlainText() { return plainText; }
    public TextSource getTextSource() { return textSource; }
}
