package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;

/** A technician's configured maximum concurrent workload would be exceeded. */
public class WorkloadExceededException extends ItsmException {
    public WorkloadExceededException(String technicianName, int max) {
        super("WORKLOAD_EXCEEDED",
              "Technician " + technicianName + " already has the maximum of " + max + " active incidents",
              HttpStatus.CONFLICT);
    }
}
