package com.finpilot.payments;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Payment aggregate. All status changes go through {@link #transitionTo}, which enforces the
 * state machine and appends the audit history entry in the same unit of work.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_reference", nullable = false, length = 40, updatable = false)
    private String paymentReference;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64, updatable = false)
    private String requestHash;

    @Column(name = "debtor_account", length = 34, updatable = false)
    private String debtorAccount;

    @Column(name = "creditor_account", length = 34, updatable = false)
    private String creditorAccount;

    @Column(name = "creditor_name", length = 140, updatable = false)
    private String creditorName;

    @Column(length = 255, updatable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    @OrderBy("sequenceNo ASC")
    private List<PaymentStatusHistory> statusHistory = new ArrayList<>();

    protected Payment() {
        // required by JPA
    }

    /** Creates a payment in CREATED state with its first history entry. */
    public static Payment create(CreatePaymentRequest request, String paymentReference, String idempotencyKey,
                                 String requestHash, String correlationId, Instant now) {
        Payment p = new Payment();
        p.paymentReference = paymentReference;
        p.idempotencyKey = idempotencyKey;
        p.requestHash = requestHash;
        p.debtorAccount = request.debtorAccount();
        p.creditorAccount = request.creditorAccount();
        p.creditorName = request.creditorName().trim();
        p.description = request.description();
        p.amount = request.amount().setScale(4, RoundingMode.UNNECESSARY);
        p.currency = request.currency();
        p.correlationId = correlationId;
        p.status = PaymentStatus.CREATED;
        p.createdAt = now;
        p.updatedAt = now;
        p.statusHistory.add(new PaymentStatusHistory(p, 1, null, PaymentStatus.CREATED,
                "Payment created", correlationId, now));
        return p;
    }

    public void transitionTo(PaymentStatus target, String reason, String correlationId, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidPaymentTransitionException(status, target);
        }
        boolean needsReason = target == PaymentStatus.FAILED || target == PaymentStatus.REQUIRES_REVIEW;
        if (needsReason && (reason == null || reason.isBlank())) {
            throw new InvalidPaymentTransitionException("A reason is required when moving a payment to " + target);
        }
        PaymentStatus from = status;
        status = target;
        updatedAt = now;
        if (target == PaymentStatus.FAILED) {
            failureReason = reason.trim();
        }
        statusHistory.add(new PaymentStatusHistory(this, statusHistory.size() + 1, from, target,
                reason == null ? null : reason.trim(), correlationId, now));
    }

    public UUID getId() { return id; }
    public String getPaymentReference() { return paymentReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getDebtorAccount() { return debtorAccount; }
    public String getCreditorAccount() { return creditorAccount; }
    public String getCreditorName() { return creditorName; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public List<PaymentStatusHistory> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }
}
