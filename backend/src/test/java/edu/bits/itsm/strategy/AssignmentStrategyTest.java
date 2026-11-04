package edu.bits.itsm.strategy;

import edu.bits.itsm.domain.incident.*;
import edu.bits.itsm.domain.service.BusinessCriticality;
import edu.bits.itsm.domain.service.ITService;
import edu.bits.itsm.domain.strategy.assignment.*;
import edu.bits.itsm.domain.user.Employee;
import edu.bits.itsm.domain.user.Technician;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** TC-17 .. TC-21: technician assignment strategies. */
class AssignmentStrategyTest {

    private Incident incident;
    private IncidentCategory network, hardware;
    private Technician skilledBusy, skilledFree, unskilledFree, unavailable, full;

    private static Technician tech(long id, String name, int exp, int max, boolean available, IncidentCategory... skills) throws Exception {
        Technician t = new Technician(name, "h", name, name + "@x.com", null, exp, max);
        t.setSkills(Set.of(skills)); t.setAvailable(available);
        Field f = edu.bits.itsm.domain.user.User.class.getDeclaredField("id"); f.setAccessible(true); f.set(t, id);
        return t;
    }
    private static IncidentCategory cat(long id, String name) throws Exception {
        IncidentCategory c = new IncidentCategory(name, "", "");
        Field f = IncidentCategory.class.getDeclaredField("id"); f.setAccessible(true); f.set(c, id);
        return c;
    }

    @BeforeEach
    void setUp() throws Exception {
        network = cat(1, "NETWORK"); hardware = cat(2, "HARDWARE");
        skilledBusy = tech(1, "skilledBusy", 9, 5, true, network);
        skilledFree = tech(2, "skilledFree", 3, 5, true, network);
        unskilledFree = tech(3, "unskilledFree", 1, 5, true, hardware);
        unavailable = tech(4, "unavailable", 10, 5, false, network);
        full = tech(5, "full", 10, 2, true, network);
        ITService svc = new ITService("VPN", "", BusinessCriticality.HIGH, "", 100);
        incident = new Incident("VPN down", "cannot connect", new Employee("e", "h", "E", "e@x.com", null, null), svc, Impact.HIGH, Urgency.HIGH, 5);
        incident.classify(network, Priority.P2, 20, "t");
    }

    private List<TechnicianCandidate> candidates() {
        return List.of(new TechnicianCandidate(skilledBusy, 4, 0), new TechnicianCandidate(skilledFree, 1, 0),
                new TechnicianCandidate(unskilledFree, 0, 0), new TechnicianCandidate(unavailable, 0, 0),
                new TechnicianCandidate(full, 2, 0));
    }

    @Test @DisplayName("TC-17 Skill-based picks the least-loaded technician WITH the skill, ignoring unavailable/full ones")
    void skillBased() {
        Optional<TechnicianCandidate> pick = new SkillBasedAssignmentStrategy().select(incident, candidates());
        assertEquals("skilledFree", pick.orElseThrow().technician().getUsername());
    }

    @Test @DisplayName("TC-18 Load-balanced picks the least-loaded available technician even without the skill")
    void loadBalanced() {
        Optional<TechnicianCandidate> pick = new LoadBalancedAssignmentStrategy().select(incident, candidates());
        assertEquals("unskilledFree", pick.orElseThrow().technician().getUsername());
    }

    @Test @DisplayName("TC-19 Exception path: no eligible technician → empty result (service raises TechnicianUnavailableException)")
    void nobodyEligible() throws Exception {
        List<TechnicianCandidate> none = List.of(new TechnicianCandidate(unavailable, 0, 0), new TechnicianCandidate(full, 2, 0));
        assertTrue(new SkillBasedAssignmentStrategy().select(incident, none).isEmpty());
        assertTrue(new LoadBalancedAssignmentStrategy().select(incident, none).isEmpty());
        assertTrue(new RoundRobinAssignmentStrategy().select(incident, none).isEmpty());
        assertTrue(new SmartRecommendationStrategy().select(incident, none).isEmpty());
    }

    @Test @DisplayName("TC-20 Round-robin rotates through eligible technicians in order")
    void roundRobin() {
        RoundRobinAssignmentStrategy rr = new RoundRobinAssignmentStrategy();
        assertEquals("skilledBusy", rr.select(incident, candidates()).orElseThrow().technician().getUsername());
        assertEquals("skilledFree", rr.select(incident, candidates()).orElseThrow().technician().getUsername());
        assertEquals("unskilledFree", rr.select(incident, candidates()).orElseThrow().technician().getUsername());
        assertEquals("skilledBusy", rr.select(incident, candidates()).orElseThrow().technician().getUsername(), "wraps around");
    }

    @Test @DisplayName("TC-21 Smart recommendation weighs skill, capacity, experience and familiarity")
    void smart() {
        SmartRecommendationStrategy s = new SmartRecommendationStrategy();
        TechnicianCandidate a = new TechnicianCandidate(skilledFree, 1, 0);   // 40 + 20 + 6 + 0 = 66
        TechnicianCandidate b = new TechnicianCandidate(skilledBusy, 4, 5);   // 40 + 5 + 18 + 15 = 78
        TechnicianCandidate c = new TechnicianCandidate(unskilledFree, 0, 0); // 0 + 25 + 2 + 0 = 27
        assertEquals(66, s.score(incident, a), 0.01);
        assertEquals(78, s.score(incident, b), 0.01);
        assertEquals(27, s.score(incident, c), 0.01);
        assertEquals("skilledBusy", s.select(incident, List.of(a, b, c)).orElseThrow().technician().getUsername());
    }
}
