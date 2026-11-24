package com.finpilot.payments;

import java.util.List;

public record PaymentDetailResponse(
        PaymentResponse payment,
        List<StatusHistoryResponse> history,
        List<PaymentStatus> allowedTransitions) {

    static PaymentDetailResponse from(Payment p) {
        return new PaymentDetailResponse(
                PaymentResponse.from(p),
                p.getStatusHistory().stream().map(StatusHistoryResponse::from).toList(),
                List.copyOf(p.getStatus().allowedTargets()));
    }
}
