package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;

/** Generic conflict (e.g. username already taken, service name already exists). */
public class ConflictException extends ItsmException {
    public ConflictException(String message) {
        super("CONFLICT", message, HttpStatus.CONFLICT);
    }
}
