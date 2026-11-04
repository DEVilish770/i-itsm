package edu.bits.itsm.service;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.sla.SLAPolicy;
import edu.bits.itsm.exception.MissingSlaConfigurationException;
import edu.bits.itsm.repository.SLAPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Looks up SLA policies and applies them to incidents. */
@Service
public class SLAService {

    private final SLAPolicyRepository policies;

    public SLAService(SLAPolicyRepository policies) { this.policies = policies; }

    public SLAPolicy policyFor(Priority p) {
        return policies.findByPriority(p).orElseThrow(() -> new MissingSlaConfigurationException(p));
    }

    /** Starts (or restarts, on priority change) the SLA clock. */
    public void apply(Incident incident, LocalDateTime from) {
        incident.getSla().applyPolicy(policyFor(incident.getPriority()), from);
    }

    public List<SLAPolicy> all() { return policies.findAll(); }

    @Transactional
    public SLAPolicy update(Priority p, int responseMinutes, int resolutionMinutes, int warnPct) {
        SLAPolicy policy = policies.findByPriority(p).orElseGet(() -> new SLAPolicy(p, responseMinutes, resolutionMinutes));
        policy.setResponseMinutes(responseMinutes);
        policy.setResolutionMinutes(resolutionMinutes);
        policy.setWarningThresholdPct(warnPct);
        return policies.save(policy);
    }
}
