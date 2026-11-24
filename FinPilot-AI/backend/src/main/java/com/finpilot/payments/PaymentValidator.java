package com.finpilot.payments;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Business validation. Malformed input is rejected earlier (HTTP 400); violations here produce a FAILED payment. */
@Component
public class PaymentValidator {

    private final BigDecimal maxAmount;

    public PaymentValidator(@Value("${finpilot.payments.max-amount:1000000}") BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public Optional<String> findViolation(CreatePaymentRequest request) {
        if (request.debtorAccount().equals(request.creditorAccount())) {
            return Optional.of("Debtor and creditor accounts must differ");
        }
        if (request.amount().compareTo(maxAmount) > 0) {
            return Optional.of("Amount exceeds the single-payment limit of " + maxAmount.toPlainString());
        }
        try {
            Currency.getInstance(request.currency());
        } catch (IllegalArgumentException e) {
            return Optional.of("Unsupported currency code " + request.currency());
        }
        return Optional.empty();
    }
}
