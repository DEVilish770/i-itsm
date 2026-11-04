package edu.bits.itsm.event.observers;

import edu.bits.itsm.domain.audit.AuditRecord;
import edu.bits.itsm.event.IncidentEvent;
import edu.bits.itsm.event.IncidentObserver;
import edu.bits.itsm.repository.AuditRecordRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Writes one audit record per event. Ordered first so history is never lost even if notification fails. */
@Component
@Order(1)
public class AuditObserver implements IncidentObserver {

    private final AuditRecordRepository repository;

    public AuditObserver(AuditRecordRepository repository) { this.repository = repository; }

    @Override
    public void onIncidentEvent(IncidentEvent e) {
        repository.save(new AuditRecord(
                e.incident().getId(), e.incident().getIncidentNumber(), e.actor(), e.type().name(),
                e.previousStatus() == null ? null : e.previousStatus().name(),
                e.newStatus() == null ? null : e.newStatus().name(),
                e.description()));
    }
}
