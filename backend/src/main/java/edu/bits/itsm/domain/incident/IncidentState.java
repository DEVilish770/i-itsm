package edu.bits.itsm.domain.incident;

import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.exception.InvalidStateTransitionException;

import java.util.Set;

/**
 * <b>State pattern</b> contract. Each lifecycle state decides which actions are
 * legal. The default implementation of every action throws
 * {@link InvalidStateTransitionException}; concrete states override only the
 * actions they permit. This guarantees, by construction, that NEW → CLOSED and
 * other illegal jumps are impossible.
 */
public interface IncidentState {

    IncidentStatus status();

    /** Names of actions a UI may offer in this state. */
    Set<String> allowedActions();

    default void classify(Incident i)                        { throw deny("classify"); }
    default void assign(Incident i, Technician t)            { throw deny("assign"); }
    default void acknowledge(Incident i)                     { throw deny("acknowledge"); }
    default void startInvestigation(Incident i)              { throw deny("startInvestigation"); }
    default void requestInfo(Incident i)                     { throw deny("requestInfo"); }
    default void provideInfo(Incident i)                     { throw deny("provideInfo"); }
    default void resolve(Incident i, Resolution r)           { throw deny("resolve"); }
    default void verify(Incident i)                          { throw deny("verify"); }
    default void reopen(Incident i)                          { throw deny("reopen"); }
    default void close(Incident i, User by, String notes)    { throw deny("close"); }

    default InvalidStateTransitionException deny(String action) {
        return new InvalidStateTransitionException(status(), action);
    }
}
