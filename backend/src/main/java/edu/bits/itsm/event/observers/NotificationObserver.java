package edu.bits.itsm.event.observers;

import edu.bits.itsm.ai.feature.PlainLanguageUpdateService;
import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.event.IncidentEvent;
import edu.bits.itsm.event.IncidentObserver;
import edu.bits.itsm.notification.NotificationDispatcher;
import edu.bits.itsm.notification.NotificationFactory;
import edu.bits.itsm.repository.AdministratorRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

import static edu.bits.itsm.domain.notification.NotificationChannelType.*;

/**
 * Decides WHO gets notified over WHICH channels for each event type.
 * Routing rules (documented in report):
 *  - reporter: in-app + email for lifecycle changes on their incident; on ASSIGNED, ACKNOWLEDGED, INFO_REQUESTED and
 *    RESOLVED the reporter's in-app copy also carries a plain-language line (template now, Claude's words after
 *    commit); the email keeps the template body
 *  - technician: in-app + email on assignment, SMS additionally for P1
 *  - admins: in-app + email for SLA violations and escalations
 */
@Component
@Order(2)
public class NotificationObserver implements IncidentObserver {

    private final NotificationDispatcher dispatcher;
    private final AdministratorRepository admins;
    private final PlainLanguageUpdateService plainUpdates;

    public NotificationObserver(NotificationDispatcher dispatcher, AdministratorRepository admins, PlainLanguageUpdateService plainUpdates) {
        this.dispatcher = dispatcher;
        this.admins = admins;
        this.plainUpdates = plainUpdates;
    }

    @Override
    public void onIncidentEvent(IncidentEvent e) {
        Incident i = e.incident();
        NotificationFactory.Message msg = NotificationFactory.forEvent(e);
        User reporter = i.getReporter();
        User tech = i.getAssignedTechnician();
        String type = e.type().name();

        switch (e.type()) {
            case CREATED, PRIORITY_CHANGED, CLOSED ->
                    dispatcher.send(reporter, msg, i.getId(), type, IN_APP, EMAIL);
            case ACKNOWLEDGED, RESOLVED, INFO_REQUESTED ->
                    notifyReporterPlainly(e, msg, IN_APP, EMAIL);
            case ASSIGNED -> {
                notifyReporterPlainly(e, msg, IN_APP);
                NotificationChannelType[] techChannels = i.getPriority() == Priority.P1
                        ? new NotificationChannelType[]{IN_APP, EMAIL, SMS} : new NotificationChannelType[]{IN_APP, EMAIL};
                dispatcher.send(tech, msg, i.getId(), type, techChannels);
            }
            case INFO_PROVIDED, VERIFIED, REOPENED, FEEDBACK_GIVEN ->
                    dispatcher.send(tech, msg, i.getId(), type, IN_APP, EMAIL);
            case SLA_WARNING -> {
                dispatcher.send(tech, msg, i.getId(), type, IN_APP, SMS);
                admins.findByActiveTrue().forEach(a -> dispatcher.send(a, msg, i.getId(), type, IN_APP));
            }
            case SLA_VIOLATED, ESCALATED -> {
                dispatcher.send(tech, msg, i.getId(), type, IN_APP, EMAIL, SMS);
                admins.findByActiveTrue().forEach(a -> dispatcher.send(a, msg, i.getId(), type, IN_APP, EMAIL));
            }
            case DUPLICATE_DETECTED ->
                    admins.findByActiveTrue().forEach(a -> dispatcher.send(a, msg, i.getId(), type, IN_APP));
            default -> { /* NOTE_ADDED, CLASSIFIED, INVESTIGATION_STARTED: audit only */ }
        }
    }

    /** Saves the reporter's copy with the template line, then lets Claude reword it once this transaction commits. */
    private void notifyReporterPlainly(IncidentEvent e, NotificationFactory.Message msg, NotificationChannelType... channels) {
        Incident i = e.incident();
        List<Notification> sent = dispatcher.send(i.getReporter(), msg.withPlainText(plainUpdates.rulesText(e)), i.getId(), e.type().name(), channels);
        plainUpdates.rewriteAfterCommit(e, sent);
    }
}
