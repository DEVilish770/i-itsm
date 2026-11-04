package edu.bits.itsm.domain.strategy.priority;

import edu.bits.itsm.domain.incident.Impact;
import edu.bits.itsm.domain.incident.Urgency;
import edu.bits.itsm.domain.service.BusinessCriticality;

/** Immutable input to a {@link PriorityStrategy}. A record keeps it a pure value. */
public record PriorityContext(Impact impact, Urgency urgency, BusinessCriticality criticality,
                              int affectedUsers, int serviceUserBase) { }
