package com.finpilot.payments;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("This Idempotency-Key was already used with a different request body");
    }
}
