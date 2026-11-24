package com.finpilot.payments;

import java.time.Instant;

public record StatusHistoryResponse(
        PaymentStatus fromStatus,
        PaymentStatus toStatus,
        String reason,
        String correlationId,
        Instant changedAt) {

    static StatusHistoryResponse from(PaymentStatusHistory h) {
        return new StatusHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getReason(),
                h.getCorrelationId(), h.getChangedAt());
    }
}
