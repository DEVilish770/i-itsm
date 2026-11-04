package edu.bits.itsm.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <b>Observer pattern</b> "subject". Services publish; observers (audit, notification,
 * dashboard broadcaster, escalation) subscribe. Spring injects every {@link IncidentObserver}
 * bean, so adding a new reaction to incidents never touches the publishing code.
 * Observers are isolated: one failing observer does not prevent the others from running.
 */
@Component
public class IncidentEventBus {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventBus.class);
    private final List<IncidentObserver> observers = new CopyOnWriteArrayList<>();

    public IncidentEventBus(List<IncidentObserver> springObservers) {
        observers.addAll(springObservers);
        log.info("IncidentEventBus initialised with {} observers: {}", observers.size(),
                observers.stream().map(o -> o.getClass().getSimpleName()).toList());
    }

    public void subscribe(IncidentObserver observer) { observers.add(observer); }
    public void unsubscribe(IncidentObserver observer) { observers.remove(observer); }

    public void publish(IncidentEvent event) {
        for (IncidentObserver o : observers) {
            try {
                o.onIncidentEvent(event);
            } catch (RuntimeException ex) {
                log.error("Observer {} failed on event {}: {}", o.getClass().getSimpleName(), event.type(), ex.getMessage());
            }
        }
    }

    public int observerCount() { return observers.size(); }
}
