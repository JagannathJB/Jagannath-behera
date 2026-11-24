package com.finpilot.payments;

public class InvalidPaymentTransitionException extends RuntimeException {

    public InvalidPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Cannot move a payment from " + from + " to " + to);
    }

    public InvalidPaymentTransitionException(String message) {
        super(message);
    }
}
