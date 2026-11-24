package com.finpilot.payments;

import java.util.EnumSet;
import java.util.Set;

/** Payment lifecycle. The transition table below is the single source of truth for the state machine. */
public enum PaymentStatus {
    CREATED,
    VALIDATED,
    PROCESSING,
    REQUIRES_REVIEW,
    COMPLETED,
    FAILED;

    public Set<PaymentStatus> allowedTargets() {
        return switch (this) {
            case CREATED -> EnumSet.of(VALIDATED, FAILED);
            case VALIDATED -> EnumSet.of(PROCESSING, REQUIRES_REVIEW, FAILED);
            case PROCESSING -> EnumSet.of(COMPLETED, REQUIRES_REVIEW, FAILED);
            case REQUIRES_REVIEW -> EnumSet.of(PROCESSING, FAILED);
            case COMPLETED, FAILED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }
}
