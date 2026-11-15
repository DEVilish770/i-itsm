package edu.bits.itsm.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiTestSupport;
import edu.bits.itsm.ai.FakeAiGateway;
import edu.bits.itsm.ai.FakeAiGatewayConfig;
import edu.bits.itsm.ai.gateway.AiMessage;
import edu.bits.itsm.ai.gateway.AiRequest;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TC-70 .. TC-77, TC-86, TC-87: the AI endpoints through the real HTTP layer (security filter, controllers, services, H2, observers)
 * with AI switched on and the fake gateway in place of Claude. Own in-memory database, so the counts asserted by
 * {@link IncidentApiIntegrationTest} are unaffected.
 */
@SpringBootTest(properties = {"itsm.ai.enabled=true", "spring.datasource.url=jdbc:h2:mem:itsm-ai-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeAiGatewayConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FakeAiGateway fake;
    @SpyBean SimpMessagingTemplate ws;

    static String employee, technician, admin;
    static long incidentId;

    private String login(String user) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"password123\"}")).andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode body(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString()); }

    private String switchAi(boolean on) { return "{\"enabled\":" + on + "}"; }

    @BeforeEach
    void setUp() throws Exception {
        if (employee == null) { employee = login("emp.raj"); technician = login("tech.arjun"); admin = login("admin"); }
        fake.reset();
    }

    @Test @Order(1) @DisplayName("TC-70 Security: drafting helpers are for employees and admins, admin AI endpoints for admins only, no token = 401")
    void security() throws Exception {
        mvc.perform(get("/api/ai/status")).andExpect(status().isUnauthorized());
        String triage = "{\"imageBase64\":\"" + AiTestSupport.PNG_BASE64 + "\",\"mediaType\":\"image/png\"}";
        mvc.perform(post("/api/ai/screenshot-triage").header("Authorization", technician).contentType(MediaType.APPLICATION_JSON).content(triage))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/chat").header("Authorization", technician).contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/ai").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(switchAi(false)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/summary").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/summary").header("Authorization", technician)).andExpect(status().isForbidden());
        assertTrue(fake.requests().isEmpty(), "a refused request never reaches Claude");
        mvc.perform(get("/api/ai/status").header("Authorization", technician)).andExpect(status().isOk());
        mvc.perform(post("/api/ai/search").header("Authorization", technician).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"vpn\"}")).andExpect(status().isOk());
    }

    @Test @Order(2) @DisplayName("TC-71 Status is LIVE with model and limit; the admin switch turns every feature to RULES and back")
    void statusAndAdminSwitch() throws Exception {
        mvc.perform(get("/api/ai/status").header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LIVE")).andExpect(jsonPath("$.reason").value(nullValue()))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5")).andExpect(jsonPath("$.dailyLimit").value(150))
                .andExpect(jsonPath("$.callsToday").isNumber());
        mvc.perform(put("/api/admin/ai").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(switchAi(false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("RULES")).andExpect(jsonPath("$.reason").value("DISABLED"));
        mvc.perform(post("/api/ai/search").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"printer on floor 2\"}"))
                .andExpect(jsonPath("$.source").value("RULES")).andExpect(jsonPath("$.filters.q").value("printer on floor 2"));
        assertTrue(fake.requests().isEmpty());
        mvc.perform(put("/api/admin/ai").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(switchAi(true)))
                .andExpect(jsonPath("$.mode").value("LIVE"));
    }

    @Test @Order(3) @DisplayName("TC-72 Screenshot to ticket: the image reaches Claude with the live catalog; the draft is validated; bad images are 400")
    void screenshotTriage() throws Exception {
        fake.answer(AiFeature.SCREENSHOT_TRIAGE, """
                {"summary":"Checkout page shows HTTP 500","draft":{"title":"Checkout fails with error 500",
                 "description":"The payment page shows Internal Server Error 500.","serviceId":1,"categoryId":999,
                 "impact":"HIGH","urgency":"BOGUS","affectedUsers":40}}""");
        mvc.perform(post("/api/ai/screenshot-triage").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageBase64\":\"" + AiTestSupport.PNG_BASE64 + "\",\"mediaType\":\"image/png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI")).andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.summary").value("Checkout page shows HTTP 500"))
                .andExpect(jsonPath("$.draft.title").value("Checkout fails with error 500"))
                .andExpect(jsonPath("$.draft.serviceId").value(1))
                .andExpect(jsonPath("$.draft.categoryId").value(nullValue()))
                .andExpect(jsonPath("$.draft.impact").value("HIGH"))
                .andExpect(jsonPath("$.draft.urgency").value(nullValue()))
                .andExpect(jsonPath("$.draft.affectedUsers").value(40))
                .andExpect(jsonPath("$.message").doesNotExist());
        AiRequest sent = fake.requests(AiFeature.SCREENSHOT_TRIAGE).get(0);
        AiMessage first = sent.messages().get(0);
        assertEquals(new AiMessage.Image("image/png", AiTestSupport.PNG_BASE64), first.image());
        assertTrue(sent.system().contains("1: Payment Application - Customer payment & checkout"));

        String jpegBytes = Base64.getEncoder().encodeToString(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'});
        String tooBig = Base64.getEncoder().encodeToString(new byte[5 * 1024 * 1024 + 1]);
        for (String bad : new String[]{
                "{\"imageBase64\":\"" + AiTestSupport.PNG_BASE64 + "\",\"mediaType\":\"image/bmp\"}",
                "{\"imageBase64\":\"data:image/png;base64," + AiTestSupport.PNG_BASE64 + "\",\"mediaType\":\"image/png\"}",
                "{\"imageBase64\":\"" + jpegBytes + "\",\"mediaType\":\"image/png\"}",
                "{\"imageBase64\":\"not base64 at all!\",\"mediaType\":\"image/png\"}",
                "{\"imageBase64\":\"" + tooBig + "\",\"mediaType\":\"image/png\"}"}) {
            mvc.perform(post("/api/ai/screenshot-triage").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AI_REQUEST"));
        }
        assertEquals(1, fake.requests().size(), "rejected images never reach Claude");
    }

    @Test @Order(4) @DisplayName("TC-73 Chat over HTTP: a follow-up question comes back; more than 12 messages is a 400")
    void chat() throws Exception {
        fake.answer(AiFeature.CHAT, "{\"done\":false,\"reply\":\"Is it only you, or your whole team?\",\"draft\":null}");
        mvc.perform(post("/api/ai/chat").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"Outlook will not open\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI")).andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.reply").value("Is it only you, or your whole team?")).andExpect(jsonPath("$.draft").value(nullValue()));
        String thirteen = IntStream.range(0, 13).mapToObj(n -> "{\"role\":\"user\",\"content\":\"m" + n + "\"}")
                .reduce((a, b) -> a + "," + b).orElseThrow();
        mvc.perform(post("/api/ai/chat").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[" + thirteen + "]}")).andExpect(status().isBadRequest());
    }

    @Test @Order(5) @DisplayName("TC-74 Plain-English search returns only whitelisted, validated filters, usable on GET /api/incidents")
    void search() throws Exception {
        fake.answer(AiFeature.SEARCH, """
                {"q":null,"status":"IN_PROGRESS","priority":"P1","categoryId":null,"serviceId":1,"technicianId":99999,
                 "slaStatus":null,"from":null,"to":null,"explanation":"Urgent payment incidents being worked on, by technician 99999."}""");
        MvcResult r = mvc.perform(post("/api/ai/search").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"urgent payment problems being worked on\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.filters.*", hasSize(3)))
                .andExpect(jsonPath("$.filters.status").value("IN_PROGRESS")).andExpect(jsonPath("$.filters.priority").value("P1"))
                .andExpect(jsonPath("$.filters.serviceId").value(1)).andExpect(jsonPath("$.filters.technicianId").doesNotExist())
                .andExpect(jsonPath("$.explanation").value(
                        "Showing incidents with priority P1 (critical), status in progress, on Payment Application."))
                .andReturn();
        JsonNode filters = body(r).get("filters");
        mvc.perform(get("/api/incidents").header("Authorization", admin)
                        .param("status", filters.get("status").asText()).param("priority", filters.get("priority").asText())
                        .param("serviceId", filters.get("serviceId").asText()))
                .andExpect(status().isOk());
    }

    @Test @Order(6) @DisplayName("TC-75 Plain-language update: template saved first, the request does not wait, then Claude's words replace it over WebSocket")
    void asyncPlainLanguageUpdate() throws Exception {
        long reporterId = body(mvc.perform(get("/api/auth/me").header("Authorization", employee)).andReturn()).get("id").asLong();
        fake.hold(AiFeature.STATUS_MESSAGE);    // Claude "thinks" until released: a synchronous design would block the create below

        JsonNode created = body(mvc.perform(post("/api/incidents").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Payroll export button does nothing\",\"description\":\"Clicking export in the HR portal shows no file\","
                                + "\"serviceId\":6,\"impact\":\"MEDIUM\",\"urgency\":\"MEDIUM\",\"affectedUsers\":3,\"autoAssign\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ASSIGNED")).andReturn());
        incidentId = created.get("id").asLong();
        String template = "Your issue \"Payroll export button does nothing\" (" + created.get("incidentNumber").asText()
                + ") has been given to " + created.get("technician").get("name").asText() + " from IT support, who will look into it.";
        mvc.perform(get("/api/notifications").header("Authorization", employee))
                .andExpect(jsonPath("$[?(@.eventType == 'ASSIGNED' && @.incidentId == " + incidentId + ")].plainText", contains(template)))
                .andExpect(jsonPath("$[?(@.eventType == 'ASSIGNED' && @.incidentId == " + incidentId + ")].textSource", contains("RULES")));

        String friendly = "Good news: someone from IT support is now looking at your payroll export problem ("
                + created.get("incidentNumber").asText() + ").";
        fake.answer(AiFeature.STATUS_MESSAGE, "{\"message\":\"" + friendly + "\"}");
        fake.release();
        ArgumentMatcher<Object> aiUpdate = p -> p instanceof Map<?, ?> m && "AI".equals(m.get("textSource")) && friendly.equals(m.get("plainText"));
        verify(ws, timeout(5000)).convertAndSend(eq("/topic/user/" + reporterId), argThat(aiUpdate));
        mvc.perform(get("/api/notifications").header("Authorization", employee))
                .andExpect(jsonPath("$[?(@.eventType == 'ASSIGNED' && @.incidentId == " + incidentId + ")].plainText", contains(friendly)))
                .andExpect(jsonPath("$[?(@.eventType == 'ASSIGNED' && @.incidentId == " + incidentId + ")].textSource", contains("AI")))
                .andExpect(jsonPath("$[?(@.eventType == 'CREATED')].textSource", everyItem(nullValue())));
        String facts = fake.requests(AiFeature.STATUS_MESSAGE).get(0).messages().get(0).text();
        assertFalse(facts.contains("P3") || facts.contains("SLA"), "Claude is given plain facts, no priority codes");
    }

    @Test @Order(7) @DisplayName("TC-76 A jargon-filled AI update is discarded: the reporter keeps the template")
    void jargonKeepsTemplate() throws Exception {
        fake.answer(AiFeature.STATUS_MESSAGE, "{\"message\":\"Your P3 ticket was acknowledged within SLA.\"}");
        mvc.perform(post("/api/incidents/" + incidentId + "/acknowledge").header("Authorization", admin)).andExpect(status().isOk());
        ArgumentMatcher<Object> acknowledgedByAi = p -> p instanceof Map<?, ?> m && "ACKNOWLEDGED".equals(m.get("eventType")) && "AI".equals(m.get("textSource"));
        verify(ws, after(1500).never()).convertAndSend(anyString(), argThat(acknowledgedByAi));
        assertEquals(1, fake.requests(AiFeature.STATUS_MESSAGE).size(), "Claude was asked once");
        mvc.perform(get("/api/notifications").header("Authorization", employee))
                .andExpect(jsonPath("$[?(@.eventType == 'ACKNOWLEDGED' && @.channel == 'IN_APP')].textSource", contains("RULES")))
                .andExpect(jsonPath("$[?(@.eventType == 'ACKNOWLEDGED' && @.channel == 'EMAIL')].textSource", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.eventType == 'ACKNOWLEDGED' && @.channel == 'EMAIL')].plainText", contains(nullValue())));
    }

    @Test @Order(8) @DisplayName("TC-77 Manager summary: AI text with an ISO-8601 time, reused while the figures are unchanged; template when AI is off")
    void managerSummary() throws Exception {
        fake.answer(AiFeature.MANAGER_SUMMARY, "{\"summary\":\"One incident is open and being handled. Nothing has missed its deadline. The HR portal is the busiest service.\"}");
        for (int n = 0; n < 2; n++) {
            mvc.perform(get("/api/admin/reports/summary").header("Authorization", admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.source").value("AI")).andExpect(jsonPath("$.text", startsWith("One incident is open")))
                    .andExpect(jsonPath("$.generatedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+05:30")));
        }
        assertEquals(1, fake.requests(AiFeature.MANAGER_SUMMARY).size(), "the second load reuses the first summary");
        mvc.perform(put("/api/admin/ai").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(switchAi(false)));
        mvc.perform(get("/api/admin/reports/summary").header("Authorization", admin))
                .andExpect(jsonPath("$.source").value("RULES")).andExpect(jsonPath("$.text", startsWith("There is 1 incident in total")));
        mvc.perform(put("/api/admin/ai").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(switchAi(true)));
    }

    @Test @Order(9) @DisplayName("TC-86 Only the in-app copy carries the plain-language line: the reporter's email row has none, before or after Claude's rewrite")
    void emailRowsKeepNoPlainLine() throws Exception {
        long reporterId = body(mvc.perform(get("/api/auth/me").header("Authorization", employee)).andReturn()).get("id").asLong();
        String friendly = "IT support needs one more detail about your payroll export problem. Please answer their question on the incident page.";
        fake.answer(AiFeature.STATUS_MESSAGE, "{\"message\":\"" + friendly + "\"}");
        mvc.perform(post("/api/incidents/" + incidentId + "/start").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(post("/api/incidents/" + incidentId + "/request-info").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Which browser do you use?\"}")).andExpect(status().isOk());
        ArgumentMatcher<Object> rewritten = p -> p instanceof Map<?, ?> m && "INFO_REQUESTED".equals(m.get("eventType")) && "AI".equals(m.get("textSource"));
        verify(ws, timeout(5000)).convertAndSend(eq("/topic/user/" + reporterId), argThat(rewritten));
        mvc.perform(get("/api/notifications").header("Authorization", employee))
                .andExpect(jsonPath("$[?(@.eventType == 'INFO_REQUESTED' && @.channel == 'IN_APP')].plainText", contains(friendly)))
                .andExpect(jsonPath("$[?(@.eventType == 'INFO_REQUESTED' && @.channel == 'IN_APP')].textSource", contains("AI")))
                .andExpect(jsonPath("$[?(@.eventType == 'INFO_REQUESTED' && @.channel == 'EMAIL')].plainText", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.eventType == 'INFO_REQUESTED' && @.channel == 'EMAIL')].textSource", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.eventType == 'INFO_REQUESTED' && @.channel == 'EMAIL')].body", contains("Which browser do you use?")));
        assertEquals(1, fake.requests(AiFeature.STATUS_MESSAGE).size(), "one rewrite, for the in-app row only");
    }

    @Test @Order(10) @DisplayName("TC-87 Mark-read and Claude's rewrite write only their own columns: a row read while Claude writes stays read, and reading it later keeps Claude's words")
    void markReadAndRewriteDoNotUndoEachOther() throws Exception {
        long reporterId = body(mvc.perform(get("/api/auth/me").header("Authorization", employee)).andReturn()).get("id").asLong();
        mvc.perform(post("/api/incidents/" + incidentId + "/provide-info").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Chrome, latest version\"}")).andExpect(status().isOk());
        fake.hold(AiFeature.STATUS_MESSAGE);    // Claude is still writing while the reporter opens the bell
        String friendly = "Good news: your payroll export problem has been fixed. Please check it and confirm on the incident page.";
        fake.answer(AiFeature.STATUS_MESSAGE, "{\"message\":\"" + friendly + "\"}");
        mvc.perform(post("/api/incidents/" + incidentId + "/resolve").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"Cleared the export queue\",\"rootCause\":\"Stuck job\"}")).andExpect(status().isOk());
        JsonNode row = notification(n -> "RESOLVED".equals(n.get("eventType").asText()) && "IN_APP".equals(n.get("channel").asText()));
        assertEquals("RULES", row.get("textSource").asText(), "the template is there while Claude is still writing");
        assertFalse(row.get("read").asBoolean());
        long rowId = row.get("id").asLong();

        mvc.perform(post("/api/notifications/" + rowId + "/read").header("Authorization", technician)).andExpect(status().isOk());
        assertFalse(notification(rowId).get("read").asBoolean(), "nobody can mark another user's notification read");
        mvc.perform(post("/api/notifications/" + rowId + "/read").header("Authorization", employee)).andExpect(status().isOk());
        fake.release();
        ArgumentMatcher<Object> rewritten = p -> p instanceof Map<?, ?> m && "RESOLVED".equals(m.get("eventType")) && "AI".equals(m.get("textSource"));
        verify(ws, timeout(5000)).convertAndSend(eq("/topic/user/" + reporterId), argThat(rewritten));
        row = notification(rowId);
        assertTrue(row.get("read").asBoolean(), "Claude's rewrite did not turn the row unread again");
        assertEquals(friendly, row.get("plainText").asText());

        mvc.perform(post("/api/notifications/read-all").header("Authorization", employee)).andExpect(status().isOk());
        row = notification(rowId);
        assertEquals("AI", row.get("textSource").asText(), "marking everything read did not put the template back");
        assertEquals(friendly, row.get("plainText").asText());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", employee)).andExpect(jsonPath("$.unread").value(0));
    }

    /** The employee's notification with this id, as GET /api/notifications returns it. */
    private JsonNode notification(long id) throws Exception { return notification(n -> n.get("id").asLong() == id); }

    private JsonNode notification(Predicate<JsonNode> which) throws Exception {
        for (JsonNode n : body(mvc.perform(get("/api/notifications").header("Authorization", employee)).andReturn()))
            if (which.test(n)) return n;
        throw new AssertionError("no such notification for the employee");
    }
}
