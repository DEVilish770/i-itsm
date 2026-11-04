package edu.bits.itsm.web.dto;

import edu.bits.itsm.domain.incident.Impact;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.incident.Urgency;
import edu.bits.itsm.domain.sla.SLAStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/** Request payloads for incident endpoints. Records = immutable, validated at the boundary. */
public final class IncidentRequests {
    private IncidentRequests() { }

    public record CreateIncident(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String description,
            @NotNull Long serviceId,
            Long categoryId,
            @NotNull Impact impact,
            @NotNull Urgency urgency,
            @Min(0) @Max(1_000_000) Integer affectedUsers,
            Boolean autoAssign,
            Boolean forceCreate) { }

    public record UpdateIncident(@Size(max = 200) String title, @Size(max = 4000) String description,
                                 Impact impact, Urgency urgency, @Min(0) Integer affectedUsers) { }

    public record Reclassify(Long categoryId, Priority priority, @Size(max = 500) String reason) { }
    public record ManualAssign(@NotNull Long technicianId, @Size(max = 500) String reason) { }
    public record AutoAssign(String strategy) { }
    public record Text(@NotBlank @Size(max = 4000) String text) { }
    public record Resolve(@NotBlank @Size(max = 2000) String summary, @Size(max = 1000) String rootCause) { }
    public record FeedbackReq(@Min(1) @Max(5) int rating, @Size(max = 1000) String comment) { }

    public record SearchCriteria(String q, IncidentStatus status, Priority priority, Long categoryId, Long serviceId,
                                 Long technicianId, SLAStatus slaStatus, LocalDateTime from, LocalDateTime to) { }
}
