package edu.bits.itsm.repository;

import edu.bits.itsm.domain.user.Technician;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {
    List<Technician> findByActiveTrue();
    List<Technician> findByActiveTrueAndTeamLeadTrue();
}
