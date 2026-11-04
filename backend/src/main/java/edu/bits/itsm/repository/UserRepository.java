package edu.bits.itsm.repository;

import edu.bits.itsm.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** <b>Repository pattern</b>: collection-like access to users; the service layer never sees SQL. */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
