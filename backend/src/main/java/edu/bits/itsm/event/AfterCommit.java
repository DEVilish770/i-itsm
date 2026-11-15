package edu.bits.itsm.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a side effect (a WebSocket push) once the current transaction has committed, or straight away when there is
 * no transaction. A browser that re-fetches on a push then sees the committed data, and a change that rolls back
 * pushes nothing. A failing push is only logged: the request has already committed and must not fail because of it.
 */
public final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() { }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safely(action); }
            });
        } else {
            safely(action);
        }
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Push after commit failed: {}", ex.getMessage());
        }
    }
}
