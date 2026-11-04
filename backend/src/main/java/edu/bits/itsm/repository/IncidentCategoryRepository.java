package edu.bits.itsm.repository;

import edu.bits.itsm.domain.incident.IncidentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentCategoryRepository extends JpaRepository<IncidentCategory, Long> {
    Optional<IncidentCategory> findByName(String name);
    List<IncidentCategory> findByActiveTrue();
}
