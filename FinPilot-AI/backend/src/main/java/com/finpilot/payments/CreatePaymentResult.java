package com.finpilot.payments;

/** {@code created} is false when an earlier request with the same idempotency key is being replayed. */
public record CreatePaymentResult(PaymentResponse payment, boolean created) {
}
