package edu.bits.itsm.repository;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.sla.SLAStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository for the Incident aggregate; dynamic search goes through {@link IncidentSpecifications}. */
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByIncidentNumber(String incidentNumber);

    List<Incident> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    List<Incident> findByAssignedTechnicianIdAndStatusInOrderByPriorityAscCreatedAtAsc(Long technicianId, Collection<IncidentStatus> statuses);

    List<Incident> findByAssignedTechnicianIdOrderByCreatedAtDesc(Long technicianId);

    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    long countByStatusIn(Collection<IncidentStatus> statuses);
    long countByPriority(Priority priority);
    long countByStatus(IncidentStatus status);
    long countBySlaStatus(SLAStatus status);

    int countByAssignedTechnicianIdAndStatusIn(Long technicianId, Collection<IncidentStatus> statuses);

    int countByAssignedTechnicianIdAndServiceId(Long technicianId, Long serviceId);

    /** Open incidents on the same service created recently: duplicate-detection candidates. */
    List<Incident> findByServiceIdAndStatusInAndCreatedAtAfter(Long serviceId, Collection<IncidentStatus> statuses, LocalDateTime after);

    /** Unacknowledged incidents of a priority older than a cutoff: escalation candidates. */
    List<Incident> findByPriorityAndStatusInAndCreatedAtBefore(Priority priority, Collection<IncidentStatus> statuses, LocalDateTime before);

    @Query("select i.service.name, count(i) from Incident i group by i.service.name order by count(i) desc")
    List<Object[]> countByService();

    @Query("select coalesce(i.category.name,'UNCLASSIFIED'), count(i) from Incident i group by i.category.name order by count(i) desc")
    List<Object[]> countByCategory();

    @Query("select i.assignedTechnician.fullName, count(i) from Incident i where i.assignedTechnician is not null and i.status in :open group by i.assignedTechnician.fullName")
    List<Object[]> workloadByTechnician(@Param("open") Collection<IncidentStatus> open);

    /** Resolved incidents on the same service/category: suggested-resolution candidates. */
    List<Incident> findTop50ByServiceIdAndStatusInOrderByUpdatedAtDesc(Long serviceId, Collection<IncidentStatus> statuses);
}
