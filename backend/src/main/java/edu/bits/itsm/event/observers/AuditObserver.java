package edu.bits.itsm.event.observers;

import edu.bits.itsm.domain.audit.AuditRecord;
import edu.bits.itsm.event.IncidentEvent;
import edu.bits.itsm.event.IncidentObserver;
import edu.bits.itsm.repository.AuditRecordRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Writes one audit record per event, first in the observer order. It runs inside the request's transaction, so the
 * record commits together with the change; if a later observer's database write fails, both roll back.
 */
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
