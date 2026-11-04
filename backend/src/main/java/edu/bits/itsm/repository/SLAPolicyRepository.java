package edu.bits.itsm.repository;

import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.sla.SLAPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SLAPolicyRepository extends JpaRepository<SLAPolicy, Long> {
    Optional<SLAPolicy> findByPriority(Priority priority);
}
