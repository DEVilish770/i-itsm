package edu.bits.itsm.repository;

import edu.bits.itsm.domain.service.ITService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ITServiceRepository extends JpaRepository<ITService, Long> {
    Optional<ITService> findByNameIgnoreCase(String name);
    List<ITService> findByActiveTrue();
}
