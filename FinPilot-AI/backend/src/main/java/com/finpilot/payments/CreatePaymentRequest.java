package com.finpilot.payments;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "amount allows at most 15 integer digits and 2 decimals")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code in upper case")
        String currency,

        @NotBlank(message = "debtorAccount is required")
        @Pattern(regexp = "^[A-Z0-9]{8,34}$", message = "debtorAccount must be 8-34 upper-case letters or digits")
        String debtorAccount,

        @NotBlank(message = "creditorAccount is required")
        @Pattern(regexp = "^[A-Z0-9]{8,34}$", message = "creditorAccount must be 8-34 upper-case letters or digits")
        String creditorAccount,

        @NotBlank(message = "creditorName is required")
        @Size(max = 140, message = "creditorName must be at most 140 characters")
        String creditorName,

        @Size(max = 255, message = "description must be at most 255 characters")
        String description) {
}
