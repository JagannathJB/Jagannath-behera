package com.finpilot.payments;

import com.finpilot.common.CorrelationIdFilter;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 201 for a new payment; 200 with {@code Idempotent-Replayed: true} when the key was already used. */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreatePaymentRequest request) {
        CreatePaymentResult result = paymentService.create(idempotencyKey, request, correlationId);
        PaymentResponse body = result.payment();
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/payments/" + body.id())).body(body);
        }
        return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
    }

    @GetMapping
    public PageResponse<PaymentResponse> search(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        var criteria = new PaymentSearchCriteria(status, currency, minAmount, maxAmount, createdFrom, createdTo, reference);
        return paymentService.search(criteria, page, size, sort);
    }

    @GetMapping("/{id}")
    public PaymentDetailResponse get(@PathVariable UUID id) {
        return paymentService.getPayment(id);
    }

    /** Simulates downstream processing until Kafka-driven processing exists (Milestone 4). */
    @PostMapping("/{id}/transitions")
    public PaymentDetailResponse transition(
            @PathVariable UUID id,
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE) String correlationId,
            @Valid @RequestBody TransitionRequest request) {
        return paymentService.transition(id, request.targetStatus(), request.reason(), correlationId);
    }
}
