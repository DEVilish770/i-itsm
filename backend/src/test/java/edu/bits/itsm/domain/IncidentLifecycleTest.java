package edu.bits.itsm.domain;

import edu.bits.itsm.domain.incident.*;
import edu.bits.itsm.domain.service.BusinessCriticality;
import edu.bits.itsm.domain.service.ITService;
import edu.bits.itsm.domain.sla.SLAPolicy;
import edu.bits.itsm.domain.user.Administrator;
import edu.bits.itsm.domain.user.Employee;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** TC-01 .. TC-07: State pattern — legal lifecycle and rejected transitions (business rules). */
class IncidentLifecycleTest {

    private Incident incident;
    private Technician tech;
    private IncidentCategory category;

    @BeforeEach
    void setUp() {
        Employee reporter = new Employee("emp", "hash", "Emp Loyee", "e@x.com", null, "Finance");
        ITService svc = new ITService("Payment", "", BusinessCriticality.MISSION_CRITICAL, "IT", 1000);
        tech = new Technician("tech", "hash", "Tech Nician", "t@x.com", null, 5, 5);
        category = new IncidentCategory("APPLICATION", "", "");
        incident = new Incident("Payment down", "Checkout fails", reporter, svc, Impact.HIGH, Urgency.HIGH, 50);
        incident.getSla().applyPolicy(new SLAPolicy(Priority.P1, 5, 30), LocalDateTime.now());
    }

    @Test @DisplayName("TC-01 Normal: full lifecycle NEW→CLASSIFIED→ASSIGNED→ACKNOWLEDGED→IN_PROGRESS→RESOLVED→VERIFIED→CLOSED")
    void fullLifecycle() {
        assertEquals(IncidentStatus.NEW, incident.getStatus());
        incident.classify(category, Priority.P1, 40, "test");
        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
        incident.assign(tech);
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertSame(tech, incident.getAssignedTechnician());
        incident.acknowledge();
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());
        assertNotNull(incident.getSla().getRespondedAt(), "acknowledging must stamp the SLA response time");
        incident.startInvestigation();
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        incident.resolve(new Resolution("Restarted gateway", "Cert expired", "tech", LocalDateTime.now()));
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        incident.verify();
        assertEquals(IncidentStatus.VERIFIED, incident.getStatus());
        incident.close(new Administrator("a", "h", "A", "a@x.com", null), "ok");
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
        assertNotNull(incident.getClosedAt());
        assertTrue(incident.allowedActions().isEmpty(), "closed incidents allow no further actions");
    }

    @Test @DisplayName("TC-02 Business rule: NEW → CLOSED is rejected")
    void cannotCloseNewIncident() {
        InvalidStateTransitionException ex = assertThrows(InvalidStateTransitionException.class,
                () -> incident.close(null, "skip"));
        assertTrue(ex.getMessage().contains("NEW"));
        assertEquals(IncidentStatus.NEW, incident.getStatus(), "state must be unchanged after a rejected transition");
    }

    @Test @DisplayName("TC-03 Business rule: cannot resolve before acknowledging/starting work")
    void cannotResolveFromAssigned() {
        incident.classify(category, Priority.P1, 40, "t");
        incident.assign(tech);
        assertThrows(InvalidStateTransitionException.class,
                () -> incident.resolve(new Resolution("x", null, "t", LocalDateTime.now())));
    }

    @Test @DisplayName("TC-04 Business rule: cannot assign an unclassified incident")
    void cannotAssignNew() {
        assertThrows(InvalidStateTransitionException.class, () -> incident.assign(tech));
    }

    @Test @DisplayName("TC-05 Normal: pending-info round trip returns to IN_PROGRESS")
    void pendingInfoRoundTrip() {
        incident.classify(category, Priority.P2, 20, "t"); incident.assign(tech); incident.acknowledge(); incident.startInvestigation();
        incident.requestInfo();
        assertEquals(IncidentStatus.PENDING_INFO, incident.getStatus());
        assertThrows(InvalidStateTransitionException.class, () -> incident.resolve(new Resolution("x", null, "t", LocalDateTime.now())),
                "cannot resolve while waiting for the reporter");
        incident.provideInfo();
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
    }

    @Test @DisplayName("TC-06 Boundary: reopening a resolved incident restarts work and clears resolvedAt")
    void reopenResolved() {
        incident.classify(category, Priority.P2, 20, "t"); incident.assign(tech); incident.acknowledge(); incident.startInvestigation();
        incident.resolve(new Resolution("fixed", null, "t", LocalDateTime.now()));
        incident.reopen();
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNull(incident.getSla().getResolvedAt());
    }

    @Test @DisplayName("TC-07 Invalid input: feedback rating outside 1..5 and feedback before resolution are rejected")
    void feedbackValidation() {
        assertThrows(IllegalStateException.class, () -> incident.giveFeedback(5, "too early"));
        incident.classify(category, Priority.P2, 20, "t"); incident.assign(tech); incident.acknowledge(); incident.startInvestigation();
        incident.resolve(new Resolution("fixed", null, "t", LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> incident.giveFeedback(6, "x"));
        assertThrows(IllegalArgumentException.class, () -> incident.giveFeedback(0, "x"));
        incident.giveFeedback(4, "good");
        assertEquals(4, incident.getFeedback().getRating());
    }

    @Test @DisplayName("TC-08 Invalid input: incident requires title, description, reporter and service")
    void constructorValidation() {
        Employee r = new Employee("e", "h", "E", "e@x.com", null, null);
        ITService s = new ITService("S", "", BusinessCriticality.LOW, "", 1);
        assertThrows(IllegalArgumentException.class, () -> new Incident("", "desc", r, s, Impact.LOW, Urgency.LOW, 0));
        assertThrows(IllegalArgumentException.class, () -> new Incident("t", "  ", r, s, Impact.LOW, Urgency.LOW, 0));
        assertThrows(NullPointerException.class, () -> new Incident("t", "d", null, s, Impact.LOW, Urgency.LOW, 0));
        assertThrows(NullPointerException.class, () -> new Incident("t", "d", r, null, Impact.LOW, Urgency.LOW, 0));
        assertEquals(0, new Incident("t", "d", r, s, Impact.LOW, Urgency.LOW, -5).getAffectedUsers(), "negative counts clamp to 0");
    }
}
