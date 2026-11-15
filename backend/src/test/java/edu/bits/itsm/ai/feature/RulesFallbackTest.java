package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bits.itsm.ai.AiBackgroundTasks;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiMode;
import edu.bits.itsm.ai.AiModeReason;
import edu.bits.itsm.ai.AiModeService;
import edu.bits.itsm.ai.AiTestSupport;
import edu.bits.itsm.ai.FakeAiGateway;
import edu.bits.itsm.ai.MutableClock;
import edu.bits.itsm.ai.gateway.AiFailure;
import edu.bits.itsm.domain.incident.*;
import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.notification.TextSource;
import edu.bits.itsm.domain.service.BusinessCriticality;
import edu.bits.itsm.domain.service.ITService;
import edu.bits.itsm.domain.sla.SLAPolicy;
import edu.bits.itsm.domain.user.Employee;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.event.IncidentEvent;
import edu.bits.itsm.event.IncidentEventType;
import edu.bits.itsm.exception.InvalidAiRequestException;
import edu.bits.itsm.notification.InAppNotificationChannel;
import edu.bits.itsm.repository.NotificationRepository;
import edu.bits.itsm.service.ReportService;
import edu.bits.itsm.web.dto.AiRequests.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.bits.itsm.ai.AiTestSupport.PNG_BASE64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** TC-65 .. TC-69, TC-85: every feature has a deterministic rules answer, used in RULES mode and whenever a live call fails. */
class RulesFallbackTest {

    private final FakeAiGateway gateway = new FakeAiGateway();
    private final AiModeService ai = AiTestSupport.liveMode(gateway);
    private final LiveCatalog catalog = AiTestSupport.catalogService();

    @Test @DisplayName("TC-65 Screenshot triage: no draft, available=false and a plain sentence; a bad image is still a 400")
    void screenshotTriage() {
        ScreenshotTriageService triage = new ScreenshotTriageService(ai, catalog);
        ScreenshotTriageService.Result rules = new ScreenshotTriageService.Result(TextSource.RULES, false, null, null, ScreenshotTriageService.RULES_MESSAGE);
        gateway.fail(AiFeature.SCREENSHOT_TRIAGE, AiFailure.REFUSED);
        assertEquals(rules, triage.triage(PNG_BASE64, "image/png"), "Claude refused: rules answer");
        gateway.credentials(false);
        assertEquals(rules, triage.triage(PNG_BASE64, "image/png"), "RULES mode: no call at all");
        assertEquals(1, gateway.requests().size());
        assertThrows(InvalidAiRequestException.class, () -> triage.triage(PNG_BASE64, "image/bmp"));
    }

    @Test @DisplayName("TC-66 Chat: one reply pointing to the normal form, done=true, no draft; a second timeout in a row starts the cool-down")
    void chat() {
        ChatReportingService chat = new ChatReportingService(ai, catalog);
        ChatReportingService.Result rules = new ChatReportingService.Result(TextSource.RULES, ChatReportingService.RULES_REPLY, true, null);
        gateway.fail(AiFeature.CHAT, AiFailure.UNAVAILABLE);
        List<ChatMessage> conversation = List.of(new ChatMessage("user", "Wi-Fi is down on floor 3"));
        assertEquals(rules, chat.reply(conversation));
        assertEquals(AiMode.LIVE, ai.status().mode(), "one timeout: only this reply falls back");
        assertEquals(rules, chat.reply(conversation));
        assertEquals(AiModeReason.UNAVAILABLE, ai.status().reason());
        assertEquals(rules, chat.reply(conversation));
        assertEquals(2, gateway.requests().size(), "no third call during the cool-down");
        assertTrue(rules.reply().contains("Report incident form"));
    }

    @Test @DisplayName("TC-67 Search: the sentence becomes the free-text filter q, also when nothing Claude proposed is valid")
    void search() {
        PlainEnglishSearchService search = new PlainEnglishSearchService(ai, catalog, java.time.Clock.system(AiModeService.IST));
        gateway.answer(AiFeature.SEARCH, "{\"status\":\"WHATEVER\",\"serviceId\":999,\"explanation\":\"Open VPN incidents\"}");
        PlainEnglishSearchService.Result r = search.translate("  urgent vpn problems ");
        assertEquals(TextSource.RULES, r.source());
        assertEquals(Map.of("q", "urgent vpn problems"), r.filters().asMap());
        assertEquals("Showing incidents whose number, title or description contains \"urgent vpn problems\".", r.explanation());
        gateway.credentials(false);
        assertEquals(r, search.translate("urgent vpn problems"));
    }

    @Test @DisplayName("TC-68 Status updates: a clear, jargon-free template for each of the four events; nothing is scheduled in RULES mode")
    void statusTemplates() {
        AiBackgroundTasks background = mock(AiBackgroundTasks.class);
        PlainLanguageUpdateService updates = new PlainLanguageUpdateService(ai, mock(NotificationRepository.class),
                mock(InAppNotificationChannel.class), mock(TransactionTemplate.class), background);
        Technician priya = new Technician("tech.priya", "hash", "Priya Nair", "p@x.com", null, 5, 5);
        Incident i = new Incident("VPN drops every hour", "Disconnects", new Employee("emp", "hash", "Emp Loyee", "e@x.com", null, "Sales"),
                new ITService("VPN", "", BusinessCriticality.HIGH, "Network Ops", 1500), Impact.MEDIUM, Urgency.HIGH, 3);
        i.assignNumber("INC-000042");
        i.getSla().applyPolicy(new SLAPolicy(Priority.P2, 15, 120), LocalDateTime.now());
        i.classify(new IncidentCategory("NETWORK", "", ""), Priority.P2, 20, "test");
        String issue = "\"VPN drops every hour\" (INC-000042)";

        i.assign(priya);
        IncidentEvent assigned = IncidentEvent.of(IncidentEventType.ASSIGNED, i, "system", IncidentStatus.CLASSIFIED, "Assigned");
        assertEquals("Your issue " + issue + " has been given to Priya Nair from IT support, who will look into it.", updates.rulesText(assigned));
        i.acknowledge();
        assertEquals("Priya Nair has seen your issue " + issue + " and is working on it now.",
                updates.rulesText(IncidentEvent.of(IncidentEventType.ACKNOWLEDGED, i, "tech.priya", "Acknowledged")));
        i.startInvestigation();
        i.requestInfo();
        assertEquals("IT support has a question about your issue " + issue + ": \"Which VPN client version do you use?\" Please reply on the incident page.",
                updates.rulesText(IncidentEvent.of(IncidentEventType.INFO_REQUESTED, i, "tech.priya", "Which VPN client version do you use?")));
        i.provideInfo();
        i.resolve(new Resolution("Updated the VPN client", "Old client", "tech.priya", LocalDateTime.now()));
        String resolved = updates.rulesText(IncidentEvent.of(IncidentEventType.RESOLVED, i, "tech.priya", "Resolved"));
        assertEquals("Your issue " + issue + " has been fixed. Please check that everything works and confirm it on the incident page.", resolved);
        assertFalse(PlainLanguageUpdateService.JARGON.matcher(resolved).find());

        assertEquals(Set.of(IncidentEventType.ASSIGNED, IncidentEventType.ACKNOWLEDGED, IncidentEventType.INFO_REQUESTED,
                IncidentEventType.RESOLVED), PlainLanguageUpdateService.COVERED);
        gateway.credentials(false);
        updates.rewriteAfterCommit(assigned, List.of(new Notification(1L, "emp", NotificationChannelType.IN_APP, "s", "b", 1L, "ASSIGNED")));
        verifyNoInteractions(background);
    }

    @Test @DisplayName("TC-69 Manager summary: the report numbers in fixed sentences, with an ISO-8601 time in India time")
    void managerSummary() {
        ReportService reports = mock(ReportService.class);
        when(reports.overview()).thenReturn(Map.of("totalIncidents", 12L, "openIncidents", 5L, "criticalIncidents", 2L,
                "resolvedIncidents", 7L, "slaViolations", 1L, "slaAtRisk", 3L));
        when(reports.performance()).thenReturn(Map.of("resolvedCount", 7, "meanTimeToAcknowledgeMinutes", 4L,
                "meanTimeToResolveMinutes", 95L, "slaCompliancePct", 85.7, "averageFeedbackRating", 4.5));
        MutableClock clock = new MutableClock(ZonedDateTime.of(2026, 11, 15, 10, 30, 0, 0, AiModeService.IST).toInstant());
        ManagerSummaryService summaries = new ManagerSummaryService(reports, ai, new ObjectMapper(), clock);
        gateway.credentials(false);

        ManagerSummaryService.Result r = summaries.summarize();
        assertEquals(TextSource.RULES, r.source());
        assertEquals("There are 12 incidents in total: 5 are still open (2 of them critical) and 7 have been resolved. "
                + "1 incident has missed the SLA deadline and 3 are at risk of missing it. "
                + "On average an incident is acknowledged after 4 minutes and resolved after 95 minutes. "
                + "SLA compliance is 85.7% and the average feedback rating is 4.5 out of 5.", r.text());
        assertEquals(OffsetDateTime.of(2026, 11, 15, 10, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)), r.generatedAt());
        assertTrue(gateway.requests().isEmpty());
    }

    @Test @DisplayName("TC-85 Manager summary before anyone has rated a fix: no rating sentence (never \"0.0 out of 5\"), and Claude is not shown that 0")
    void managerSummaryWithoutRatings() {
        ReportService reports = mock(ReportService.class);
        when(reports.overview()).thenReturn(Map.of("totalIncidents", 3L, "openIncidents", 1L, "criticalIncidents", 0L,
                "resolvedIncidents", 2L, "slaViolations", 0L, "slaAtRisk", 0L));
        when(reports.performance()).thenReturn(Map.of("resolvedCount", 2, "meanTimeToAcknowledgeMinutes", 3L,
                "meanTimeToResolveMinutes", 40L, "slaCompliancePct", 100.0, "averageFeedbackRating", 0.0));
        ManagerSummaryService summaries = new ManagerSummaryService(reports, ai, new ObjectMapper(),
                new MutableClock(ZonedDateTime.of(2026, 11, 15, 10, 30, 0, 0, AiModeService.IST).toInstant()));
        gateway.credentials(false);
        assertEquals("There are 3 incidents in total: 1 is still open (0 of them critical) and 2 have been resolved. "
                + "0 incidents have missed the SLA deadline and 0 are at risk of missing it. "
                + "On average an incident is acknowledged after 3 minutes and resolved after 40 minutes. "
                + "SLA compliance is 100.0%.", summaries.summarize().text());

        gateway.credentials(true);
        gateway.answer(AiFeature.MANAGER_SUMMARY, "{\"summary\":\"Two of the three incidents are already fixed.\"}");
        assertEquals(TextSource.AI, summaries.summarize().source());
        String figures = gateway.requests(AiFeature.MANAGER_SUMMARY).get(0).messages().get(0).text();
        assertTrue(figures.contains("\"resolvedCount\":2"));
        assertFalse(figures.contains("averageFeedbackRating"), "no rating is sent while nobody has rated");
    }
}
