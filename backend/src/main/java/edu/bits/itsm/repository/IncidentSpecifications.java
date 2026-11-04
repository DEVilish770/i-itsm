package edu.bits.itsm.repository;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.sla.SLAStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * Composable search criteria (Specification pattern on top of the repository).
 * Each method is a small reusable predicate; the search endpoint ANDs the ones supplied.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() { }

    public static Specification<Incident> hasNumberOrText(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, cq, cb) -> cb.or(
                cb.like(cb.lower(root.get("incidentNumber")), like),
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("description")), like));
    }
    public static Specification<Incident> hasStatus(IncidentStatus s) {
        return s == null ? null : (root, cq, cb) -> cb.equal(root.get("status"), s);
    }
    public static Specification<Incident> hasPriority(Priority p) {
        return p == null ? null : (root, cq, cb) -> cb.equal(root.get("priority"), p);
    }
    public static Specification<Incident> hasCategory(Long categoryId) {
        return categoryId == null ? null : (root, cq, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }
    public static Specification<Incident> hasService(Long serviceId) {
        return serviceId == null ? null : (root, cq, cb) -> cb.equal(root.get("service").get("id"), serviceId);
    }
    public static Specification<Incident> hasTechnician(Long technicianId) {
        return technicianId == null ? null : (root, cq, cb) -> cb.equal(root.get("assignedTechnician").get("id"), technicianId);
    }
    public static Specification<Incident> hasReporter(Long reporterId) {
        return reporterId == null ? null : (root, cq, cb) -> cb.equal(root.get("reporter").get("id"), reporterId);
    }
    public static Specification<Incident> hasSlaStatus(SLAStatus s) {
        return s == null ? null : (root, cq, cb) -> cb.equal(root.get("sla").get("status"), s);
    }
    public static Specification<Incident> createdAfter(LocalDateTime from) {
        return from == null ? null : (root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }
    public static Specification<Incident> createdBefore(LocalDateTime to) {
        return to == null ? null : (root, cq, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
