package edu.bits.itsm.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TC-24 .. TC-33, TC-39 .. TC-42: integration tests through the real HTTP layer (security filter, controllers, services, H2 database,
 * observers). Uses the seeded demo accounts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IncidentApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    static String employeeToken, technicianToken, adminToken;
    static long incidentId, assignedIncidentId;

    private String login(String user) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"password123\"}")).andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    @BeforeEach
    void tokens() throws Exception {
        if (employeeToken == null) { employeeToken = login("emp.raj"); technicianToken = login("tech.arjun"); adminToken = login("admin"); }
    }

    @Test @Order(1) @DisplayName("TC-24 Authentication: wrong password → 401; no token → 401")
    void authFailures() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"emp.raj\",\"password\":\"nope\"}")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(get("/api/incidents/mine")).andExpect(status().isUnauthorized());
    }

    @Test @Order(2) @DisplayName("TC-25 Authorization: employee cannot reach admin endpoints (403)")
    void employeeForbiddenOnAdmin() throws Exception {
        mvc.perform(get("/api/admin/reports/overview").header("Authorization", "Bearer " + employeeToken)).andExpect(status().isForbidden());
    }

    @Test @Order(3) @DisplayName("TC-26 Invalid input: blank fields rejected with field-level messages (400)")
    void validation() throws Exception {
        mvc.perform(post("/api/incidents").header("Authorization", "Bearer " + employeeToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"serviceId\":1}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.description").exists());
    }

    @Test @Order(4) @DisplayName("TC-27 Normal: report → auto-classified, prioritised P1, SLA applied, auto-assigned, audited")
    void reportIncident() throws Exception {
        MvcResult r = mvc.perform(post("/api/incidents").header("Authorization", "Bearer " + employeeToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Payment application unavailable\",\"description\":\"Customers cannot complete checkout, payment page shows 500 error\"," +
                         "\"serviceId\":1,\"impact\":\"CRITICAL\",\"urgency\":\"HIGH\",\"affectedUsers\":400,\"autoAssign\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentNumber", startsWith("INC-")))
                .andExpect(jsonPath("$.priority").value("P1"))
                .andExpect(jsonPath("$.category.name").value("APPLICATION"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.technician.name").value("Arjun Mehta"))
                .andExpect(jsonPath("$.sla.status").value("ON_TRACK"))
                .andExpect(jsonPath("$.sla.resolutionDeadline").exists())
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        incidentId = body.get("id").asLong();

        mvc.perform(get("/api/incidents/" + incidentId + "/history").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItems("CREATED", "CLASSIFIED", "ASSIGNED")));
    }

    @Test @Order(5) @DisplayName("TC-28 Intelligent: a similar report on the same service is blocked as duplicate (409) unless forced")
    void duplicateDetection() throws Exception {
        String dup = "{\"title\":\"Payment transaction failing\",\"description\":\"Unable to make payment, checkout page gives error 500\"," +
                     "\"serviceId\":1,\"impact\":\"HIGH\",\"urgency\":\"HIGH\",\"affectedUsers\":10%s}";
        mvc.perform(post("/api/incidents").header("Authorization", "Bearer " + employeeToken).contentType(MediaType.APPLICATION_JSON)
                .content(dup.formatted(""))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_INCIDENT"))
                .andExpect(jsonPath("$.candidates", hasSize(greaterThan(0))));
        mvc.perform(post("/api/incidents").header("Authorization", "Bearer " + employeeToken).contentType(MediaType.APPLICATION_JSON)
                .content(dup.formatted(",\"forceCreate\":true"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedIncidentNumber", startsWith("INC-")));
    }

    @Test @Order(6) @DisplayName("TC-29 Authorization: employee cannot acknowledge; another technician cannot acknowledge")
    void ownershipChecks() throws Exception {
        mvc.perform(post("/api/incidents/" + incidentId + "/acknowledge").header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("UNAUTHORIZED_OPERATION"));
        String other = login("tech.priya");
        mvc.perform(post("/api/incidents/" + incidentId + "/acknowledge").header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
    }

    @Test @Order(7) @DisplayName("TC-30 Exception: invalid state transition over HTTP → 409 INVALID_STATE_TRANSITION")
    void invalidTransitionHttp() throws Exception {
        mvc.perform(post("/api/incidents/" + incidentId + "/resolve").header("Authorization", "Bearer " + technicianToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"summary\":\"too early\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test @Order(8) @DisplayName("TC-31 Normal: technician works the incident to RESOLVED; reporter verifies and closes; notifications exist")
    void workAndClose() throws Exception {
        String t = "Bearer " + technicianToken, e = "Bearer " + employeeToken;
        mvc.perform(post("/api/incidents/" + incidentId + "/acknowledge").header("Authorization", t)).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        mvc.perform(post("/api/incidents/" + incidentId + "/start").header("Authorization", t)).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(post("/api/incidents/" + incidentId + "/request-info").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Which browser?\"}")).andExpect(jsonPath("$.status").value("PENDING_INFO"));
        mvc.perform(post("/api/incidents/" + incidentId + "/provide-info").header("Authorization", e).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"All browsers\"}")).andExpect(jsonPath("$.status").value("IN_PROGRESS")).andExpect(jsonPath("$.notes", hasSize(2)));
        mvc.perform(post("/api/incidents/" + incidentId + "/resolve").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"Restarted gateway\",\"rootCause\":\"Expired cert\"}"))
                .andExpect(jsonPath("$.status").value("RESOLVED")).andExpect(jsonPath("$.sla.status").value("MET"));
        mvc.perform(post("/api/incidents/" + incidentId + "/verify").header("Authorization", e)).andExpect(jsonPath("$.status").value("VERIFIED"));
        mvc.perform(post("/api/incidents/" + incidentId + "/feedback").header("Authorization", e).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"comment\":\"great\"}")).andExpect(jsonPath("$.feedback.rating").value(5));
        mvc.perform(post("/api/incidents/" + incidentId + "/close").header("Authorization", e)).andExpect(jsonPath("$.status").value("CLOSED"));

        mvc.perform(get("/api/notifications").header("Authorization", e)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].channel", hasItems("EMAIL", "IN_APP")))
                .andExpect(jsonPath("$[*].subject", hasItem(containsString("resolved"))));
    }

    @Test @Order(9) @DisplayName("TC-32 Search & reporting: filters by status/priority/text and admin overview counts")
    void searchAndReports() throws Exception {
        String a = "Bearer " + adminToken;
        mvc.perform(get("/api/incidents?status=CLOSED").header("Authorization", a)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/incidents?priority=P1&q=payment").header("Authorization", a)).andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/incidents?serviceId=999").header("Authorization", a)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/reports/overview").header("Authorization", a))
                .andExpect(jsonPath("$.totalIncidents").value(2)).andExpect(jsonPath("$.resolvedIncidents").value(1))
                .andExpect(jsonPath("$.criticalIncidents").value(1)); // both are P1, but the closed one is no longer "critical open"
        mvc.perform(get("/api/admin/reports/performance").header("Authorization", a)).andExpect(jsonPath("$.averageFeedbackRating").value(5.0));
    }

    @Test @Order(10) @DisplayName("TC-33 Exception: unknown resources → 404; admin can switch strategy at runtime")
    void notFoundAndStrategySwitch() throws Exception {
        String a = "Bearer " + adminToken;
        mvc.perform(get("/api/incidents/99999").header("Authorization", a)).andExpect(status().isNotFound());
        mvc.perform(post("/api/incidents/" + incidentId + "/assign").header("Authorization", a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"technicianId\":99999}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/strategies").header("Authorization", a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentStrategy\":\"LOAD_BALANCED\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/strategies").header("Authorization", a)).andExpect(jsonPath("$.activeAssignment").value("LOAD_BALANCED"));
        mvc.perform(put("/api/admin/strategies").header("Authorization", a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentStrategy\":\"NOPE\"}")).andExpect(status().isNotFound());
    }

    @Test @Order(11) @DisplayName("TC-39 Authorization: only the assigned technician (or an admin) may edit or re-classify; another technician gets 403")
    void onlyAssigneeMayEditOrReclassify() throws Exception {
        String a = "Bearer " + adminToken, t = "Bearer " + technicianToken, other = "Bearer " + login("tech.priya");
        MvcResult r = mvc.perform(post("/api/incidents").header("Authorization", "Bearer " + login("emp.meera")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Outlook calendar not syncing\",\"description\":\"Meetings created on the phone never appear in the Outlook calendar\"," +
                         "\"serviceId\":3,\"impact\":\"LOW\",\"urgency\":\"LOW\",\"affectedUsers\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CLASSIFIED")).andReturn();
        assignedIncidentId = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
        long arjunId = json.readTree(mvc.perform(get("/api/auth/me").header("Authorization", t))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/incidents/" + assignedIncidentId + "/assign").header("Authorization", a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"technicianId\":" + arjunId + "}")).andExpect(jsonPath("$.status").value("ASSIGNED"));

        mvc.perform(put("/api/incidents/" + assignedIncidentId).header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Edited by someone else\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("UNAUTHORIZED_OPERATION"));
        mvc.perform(post("/api/incidents/" + assignedIncidentId + "/classify").header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\":\"P1\",\"reason\":\"not my incident\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/incidents/" + assignedIncidentId).header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Outlook calendar not syncing on phones\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Outlook calendar not syncing on phones"));
    }

    @Test @Order(12) @DisplayName("TC-40 Admin re-classification works past CLASSIFIED (status kept, SLA restarted) and is refused once CLOSED (409)")
    void adminReclassifyOutsideLifecycle() throws Exception {
        String a = "Bearer " + adminToken;
        mvc.perform(post("/api/incidents/" + assignedIncidentId + "/classify").header("Authorization", a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\":\"P2\",\"reason\":\"VIP user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.priority").value("P2"))
                .andExpect(jsonPath("$.sla.status").value("ON_TRACK"));
        mvc.perform(get("/api/incidents/" + assignedIncidentId + "/history").header("Authorization", a))
                .andExpect(jsonPath("$[*].action", hasItems("PRIORITY_CHANGED", "CLASSIFIED")));
        mvc.perform(post("/api/incidents/" + incidentId + "/classify").header("Authorization", a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\":\"P3\",\"reason\":\"too late\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test @Order(13) @DisplayName("TC-41 The bell's unread count includes only IN_APP rows, not the EMAIL/SMS copies of the same events")
    void unreadCountIsInAppOnly() throws Exception {
        String e = "Bearer " + employeeToken;
        JsonNode rows = json.readTree(mvc.perform(get("/api/notifications").header("Authorization", e))
                .andReturn().getResponse().getContentAsString());
        int inAppUnread = 0, otherUnread = 0;
        for (JsonNode n : rows) {
            if (n.get("read").asBoolean()) continue;
            if ("IN_APP".equals(n.get("channel").asText())) inAppUnread++; else otherUnread++;
        }
        Assertions.assertTrue(otherUnread > 0, "the reporter also received EMAIL copies");
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", e))
                .andExpect(jsonPath("$.unread").value(inAppUnread));
    }

    @Test @Order(14) @DisplayName("TC-42 Privacy: the duplicate warning shows an employee the number and status of someone else's incident, never its title")
    void analyzeHidesOtherPeoplesTitles() throws Exception {
        String body = "{\"title\":\"Payment transaction failing\",\"description\":\"Unable to make payment, checkout page gives error 500\",\"serviceId\":1}";
        mvc.perform(post("/api/incidents/analyze").header("Authorization", "Bearer " + login("emp.meera")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarIncidents[0].incidentNumber", startsWith("INC-")))
                .andExpect(jsonPath("$.similarIncidents[0].status").value("CLASSIFIED"))
                .andExpect(jsonPath("$.similarIncidents[0].similarity").value(100))
                .andExpect(jsonPath("$.similarIncidents[0].title").doesNotExist())
                .andExpect(jsonPath("$.similarIncidents[0].id").doesNotExist());
        mvc.perform(post("/api/incidents/analyze").header("Authorization", "Bearer " + employeeToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.similarIncidents[0].title").value("Payment transaction failing")); // the reporter's own incident
        mvc.perform(post("/api/incidents/analyze").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.similarIncidents[0].title").value("Payment transaction failing"))
                .andExpect(jsonPath("$.similarIncidents[0].id").exists());
    }
}
