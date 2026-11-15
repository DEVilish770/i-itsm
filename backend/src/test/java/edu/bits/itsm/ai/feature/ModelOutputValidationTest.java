package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bits.itsm.ai.AiTestSupport;
import edu.bits.itsm.domain.incident.Urgency;
import edu.bits.itsm.event.IncidentEventType;
import edu.bits.itsm.web.dto.IncidentRequests.CreateIncident;
import edu.bits.itsm.web.dto.IncidentRequests.SearchCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** TC-56 .. TC-60: nothing a model returns is trusted; bad enums, unknown ids, bad numbers and unknown keys are dropped. */
class ModelOutputValidationTest {

    private final ObjectMapper json = new ObjectMapper();
    private final LiveCatalog.Snapshot catalog = AiTestSupport.catalog();

    private JsonNode parse(String s) throws Exception { return json.readTree(s); }

    @Test @DisplayName("TC-56 Draft: a bad enum value or an id outside the live catalog becomes null; valid values are kept")
    void draftValidation() throws Exception {
        IncidentDraft d = IncidentDraft.fromModel(parse("""
                {"title":"  VPN drops every hour ","description":"Disconnects","serviceId":4,"categoryId":999,
                 "impact":"SEVERE","urgency":"high","affectedUsers":-3}"""), catalog);
        assertEquals(new IncidentDraft("VPN drops every hour", "Disconnects", 4L, null, null, Urgency.HIGH, null), d);
        assertEquals(new IncidentDraft(null, null, null, null, null, null, null), IncidentDraft.fromModel(parse("\"not an object\""), catalog));
        assertNull(IncidentDraft.fromModel(parse("{\"affectedUsers\":2000000}"), catalog).affectedUsers(), "above the form's maximum");
    }

    @Test @DisplayName("TC-57 Draft: field names and types are exactly those of the create-incident request, minus its form switches")
    void draftMatchesCreateIncident() {
        Map<String, Class<?>> create = Arrays.stream(CreateIncident.class.getRecordComponents())
                .collect(Collectors.toMap(RecordComponent::getName, RecordComponent::getType));
        Set<String> leftOut = new HashSet<>(create.keySet());
        for (RecordComponent c : IncidentDraft.class.getRecordComponents()) {
            assertEquals(create.get(c.getName()), c.getType(), c.getName());
            leftOut.remove(c.getName());
        }
        assertEquals(Set.of("autoAssign", "forceCreate"), leftOut);
    }

    @Test @DisplayName("TC-58 Search: only whitelisted parameters survive, each validated; unknown keys are ignored")
    void searchWhitelist() throws Exception {
        SearchFilters f = SearchFilters.fromModel(parse("""
                {"q":"vpn","status":"BOGUS","priority":"P1","serviceId":4,"categoryId":999,"technicianId":7,
                 "slaStatus":"VIOLATED","from":"2026-11-08T00:00:00","to":"not a date","sort":"title","dropTable":"incidents"}"""), catalog);
        assertEquals(Map.of("q", "vpn", "priority", "P1", "serviceId", 4L, "technicianId", 7L, "slaStatus", "VIOLATED",
                "from", "2026-11-08T00:00:00"), f.asMap());
        assertEquals(List.of("status", "categoryId", "to"), f.rejected());
    }

    @Test @DisplayName("TC-59 Search: the whitelist is exactly the existing search parameters; an impossible date range is dropped")
    void whitelistIsTheSearchCriteria() throws Exception {
        assertEquals(Arrays.stream(SearchCriteria.class.getRecordComponents()).map(RecordComponent::getName).toList(), SearchFilters.KEYS);
        SearchFilters f = SearchFilters.fromModel(parse("{\"from\":\"2026-11-10T00:00:00\",\"to\":\"2026-11-01T00:00:00\"}"), catalog);
        assertTrue(f.isEmpty());
        assertEquals(List.of("from", "to"), f.rejected());
    }

    @Test @DisplayName("TC-60 Status update: an AI sentence with jargon (SLA, P1) or over 400 characters is discarded; the title may contain anything")
    void statusMessageGuard() throws Exception {
        StatusFacts facts = new StatusFacts(IncidentEventType.RESOLVED, "INC-000042", "P2 printer jammed", "Priya Nair", "urgent", null);
        assertNull(PlainLanguageUpdateService.validMessage(parse("{\"message\":\"Your P1 ticket met its SLA.\"}"), facts));
        assertNull(PlainLanguageUpdateService.validMessage(parse("{\"message\":\"" + "a".repeat(401) + "\"}"), facts));
        String ok = "Good news: \"P2 printer jammed\" (INC-000042) has been fixed. Please check it works.";
        assertEquals(ok, PlainLanguageUpdateService.validMessage(parse("{\"message\":\"" + ok.replace("\"", "\\\"") + "\"}"), facts));
    }
}
