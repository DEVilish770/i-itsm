package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;
import java.util.List;

/** An incident that looks like an existing open incident was submitted without the override flag. */
public class DuplicateIncidentException extends ItsmException {
    private final List<String> candidates;
    public DuplicateIncidentException(List<String> candidates) {
        super("DUPLICATE_INCIDENT",
              "This looks like a duplicate of open incident(s) " + candidates + ". Re-submit with forceCreate=true to override.",
              HttpStatus.CONFLICT);
        this.candidates = candidates;
    }
    public List<String> getCandidates() { return candidates; }
}
