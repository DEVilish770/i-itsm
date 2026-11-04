package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;

/** No technician matching the assignment strategy's criteria is available right now. */
public class TechnicianUnavailableException extends ItsmException {
    public TechnicianUnavailableException(String message) {
        super("TECHNICIAN_UNAVAILABLE", message, HttpStatus.CONFLICT);
    }
}
