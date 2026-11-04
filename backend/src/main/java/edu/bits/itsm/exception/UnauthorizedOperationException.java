package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;

/** The authenticated user is not permitted to perform this operation on this resource. */
public class UnauthorizedOperationException extends ItsmException {
    public UnauthorizedOperationException(String message) {
        super("UNAUTHORIZED_OPERATION", message, HttpStatus.FORBIDDEN);
    }
}
