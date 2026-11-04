package edu.bits.itsm.exception;

import edu.bits.itsm.domain.incident.Priority;
import org.springframework.http.HttpStatus;

/** No SLA policy is configured for the computed priority. */
public class MissingSlaConfigurationException extends ItsmException {
    public MissingSlaConfigurationException(Priority priority) {
        super("MISSING_SLA", "No SLA policy configured for priority " + priority, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
