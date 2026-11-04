package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;

/** A requested user, incident, service, SLA policy or category does not exist. */
public class ResourceNotFoundException extends ItsmException {
    public ResourceNotFoundException(String resource, Object id) {
        super("NOT_FOUND", resource + " not found: " + id, HttpStatus.NOT_FOUND);
    }
}
