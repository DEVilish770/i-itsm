package edu.bits.itsm.repository;

import edu.bits.itsm.domain.user.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdministratorRepository extends JpaRepository<Administrator, Long> {
    List<Administrator> findByActiveTrue();
}
