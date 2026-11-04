package edu.bits.itsm.exception;

import edu.bits.itsm.domain.incident.IncidentStatus;
import org.springframework.http.HttpStatus;

/** Raised by the State pattern when an incident is asked to make a transition its current state forbids. */
public class InvalidStateTransitionException extends ItsmException {
    public InvalidStateTransitionException(IncidentStatus from, String action) {
        super("INVALID_STATE_TRANSITION",
              "Action '" + action + "' is not allowed while the incident is in state " + from, HttpStatus.CONFLICT);
    }
}
