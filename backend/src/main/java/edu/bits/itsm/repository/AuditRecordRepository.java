package edu.bits.itsm.repository;

import edu.bits.itsm.domain.audit.AuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    List<AuditRecord> findByIncidentIdOrderByTimestampAsc(Long incidentId);
    Page<AuditRecord> findAllByOrderByTimestampDesc(Pageable pageable);
}
