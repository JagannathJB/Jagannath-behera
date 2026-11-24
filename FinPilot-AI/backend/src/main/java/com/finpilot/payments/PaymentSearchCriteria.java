package com.finpilot.payments;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSearchCriteria(
        PaymentStatus status,
        String currency,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Instant createdFrom,
        Instant createdTo,
        String reference) {
}
