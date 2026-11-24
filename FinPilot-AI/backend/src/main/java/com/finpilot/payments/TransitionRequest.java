package com.finpilot.payments;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionRequest(
        @NotNull(message = "targetStatus is required") PaymentStatus targetStatus,
        @Size(max = 500, message = "reason must be at most 500 characters") String reason) {
}
