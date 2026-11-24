package com.finpilot.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public API representation of a payment. Fields added after the first milestone are null for legacy rows. */
public record PaymentResponse(
        UUID id,
        String paymentReference,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String debtorAccount,
        String creditorAccount,
        String creditorName,
        String description,
        String failureReason,
        String correlationId,
        Instant createdAt,
        Instant updatedAt) {

    static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getPaymentReference(), p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getDebtorAccount(), p.getCreditorAccount(), p.getCreditorName(),
                p.getDescription(), p.getFailureReason(), p.getCorrelationId(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
