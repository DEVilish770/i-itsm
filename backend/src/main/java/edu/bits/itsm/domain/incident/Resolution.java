package edu.bits.itsm.domain.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

/** Value object: how the incident was fixed. Immutable once set. */
@Embeddable
public class Resolution {

    @Column(name = "resolution_summary", length = 2000)
    private String summary;

    @Column(name = "resolution_root_cause", length = 1000)
    private String rootCause;

    @Column(name = "resolution_by", length = 60)
    private String resolvedBy;

    @Column(name = "resolution_at")
    private LocalDateTime resolvedAt;

    protected Resolution() { }

    public Resolution(String summary, String rootCause, String resolvedBy, LocalDateTime resolvedAt) {
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("Resolution summary is required");
        this.summary = summary;
        this.rootCause = rootCause;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
    }

    public String getSummary() { return summary; }
    public String getRootCause() { return rootCause; }
    public String getResolvedBy() { return resolvedBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public boolean isPresent() { return summary != null; }
}
