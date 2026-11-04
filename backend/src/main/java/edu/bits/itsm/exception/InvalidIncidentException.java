package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;

/** Incident data violates a business rule (beyond simple field validation). */
public class InvalidIncidentException extends ItsmException {
    public InvalidIncidentException(String message) {
        super("INVALID_INCIDENT", message, HttpStatus.BAD_REQUEST);
    }
}
