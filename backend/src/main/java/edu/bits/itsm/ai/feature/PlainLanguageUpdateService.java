package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.databind.JsonNode;
import edu.bits.itsm.ai.AiBackgroundTasks;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiModeService;
import edu.bits.itsm.ai.gateway.AiMessage;
import edu.bits.itsm.ai.gateway.AiRequest;
import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.notification.TextSource;
import edu.bits.itsm.event.AfterCommit;
import edu.bits.itsm.event.IncidentEvent;
import edu.bits.itsm.event.IncidentEventType;
import edu.bits.itsm.notification.InAppNotificationChannel;
import edu.bits.itsm.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Feature 4: plain-language updates for the reporter on {@link #COVERED} events. The in-app notification (the only
 * copy that shows the plain line) is saved at once with the RULES template ({@link #rulesText}); after the transaction
 * commits, a background thread asks Claude for a friendlier one-or-two-sentence version, writes it into that row (text
 * source AI) and pushes the row again through the normal WebSocket path. No HTTP request ever waits for Claude; on any
 * failure the template stays.
 */
@Service
public class PlainLanguageUpdateService {

    public static final Set<IncidentEventType> COVERED = Set.copyOf(EnumSet.of(
            IncidentEventType.ASSIGNED, IncidentEventType.ACKNOWLEDGED, IncidentEventType.INFO_REQUESTED, IncidentEventType.RESOLVED));
    static final int MESSAGE_MAX = 400;
    /** Words a reporter should never see; an AI message containing them is discarded. */
    static final Pattern JARGON = Pattern.compile("\\b(SLA|P[1-4]|PENDING_INFO|IN_PROGRESS)\\b", Pattern.CASE_INSENSITIVE);

    private static final Map<String, Object> SCHEMA = OutputSchema.object(
            "message", OutputSchema.string("One or two friendly sentences for the employee"));

    private final AiModeService ai;
    private final NotificationRepository notifications;
    private final InAppNotificationChannel inApp;
    private final TransactionTemplate tx;
    private final AiBackgroundTasks background;

    public PlainLanguageUpdateService(AiModeService ai, NotificationRepository notifications, InAppNotificationChannel inApp,
                                      TransactionTemplate tx, AiBackgroundTasks background) {
        this.ai = ai;
        this.notifications = notifications;
        this.inApp = inApp;
        this.tx = tx;
        this.background = background;
    }

    public boolean covers(IncidentEventType type) { return COVERED.contains(type); }

    /** RULES text: a clear template sentence, saved with the notification straight away. */
    public String rulesText(IncidentEvent event) { return StatusFacts.of(event).template(); }

    /**
     * Schedules the AI rewrite of the in-app rows among these just-saved ones for after the current transaction
     * commits (only when LIVE). Email and SMS rows are left alone: the message they delivered had no plain line.
     */
    public void rewriteAfterCommit(IncidentEvent event, List<Notification> saved) {
        if (saved.isEmpty() || !covers(event.type()) || !ai.isLive()) return;
        List<Long> ids = saved.stream().filter(n -> n.getChannel() == NotificationChannelType.IN_APP).map(Notification::getId).toList();
        if (ids.isEmpty()) return;
        StatusFacts facts = StatusFacts.of(event);
        AfterCommit.run(() -> background.submit(() -> rewrite(facts, ids)));
    }

    /** Runs on a background thread. */
    void rewrite(StatusFacts facts, List<Long> notificationIds) {
        aiMessage(facts).ifPresent(text -> tx.executeWithoutResult(status -> apply(text, notificationIds)));
    }

    Optional<String> aiMessage(StatusFacts facts) {
        String system = """
                You write short updates for an employee who reported an IT problem. Write one or two friendly sentences in \
                plain everyday words about what just happened to their issue. Mention the issue by its title and give the \
                incident number once. No jargon: never write SLA, P1 to P4, priority codes, status names or technical \
                abbreviations; say "urgent" or "being fixed" instead. If the technician asked a question, include it and \
                ask the employee to reply on the incident page. Do not promise times or outcomes.
                """ + Prompts.DATA_NOT_INSTRUCTIONS;
        AiRequest request = new AiRequest(AiFeature.STATUS_MESSAGE, system, List.of(AiMessage.user(facts.promptText())), SCHEMA);
        return ai.call(request).map(answer -> validMessage(answer, facts));
    }

    /** Accepts the model's sentence only if it is short and free of jargon (the issue's own title aside); null otherwise. */
    static String validMessage(JsonNode answer, StatusFacts facts) {
        String text = ModelOutput.textWithin(answer, "message", MESSAGE_MAX);
        if (text == null) return null;
        String withoutTitle = facts.title() == null ? text : text.replace(facts.title(), "");
        return JARGON.matcher(withoutTitle).find() ? null : text;
    }

    /**
     * Writes only the plain line and its source (one targeted update, no load-and-save of the whole row), so a
     * "mark read" that commits at the same moment is never undone; then re-reads the rows and pushes them.
     */
    private void apply(String text, List<Long> ids) {
        String line = Notification.fitPlainText(text);
        if (line == null || notifications.describePlainly(ids, line, TextSource.AI) == 0) return;
        for (Notification n : notifications.findAllById(ids)) {
            if (n.getChannel() == NotificationChannelType.IN_APP) inApp.deliver(n);   // pushed after this transaction commits
        }
    }
}
