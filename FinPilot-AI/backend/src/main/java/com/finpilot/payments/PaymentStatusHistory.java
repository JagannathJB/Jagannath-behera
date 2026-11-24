package com.finpilot.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only audit record of one status change. Created only through {@link Payment}. */
@Entity
@Table(name = "payment_status_history")
public class PaymentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30, updatable = false)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private PaymentStatus toStatus;

    @Column(length = 500, updatable = false)
    private String reason;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected PaymentStatusHistory() {
        // required by JPA
    }

    PaymentStatusHistory(Payment payment, int sequenceNo, PaymentStatus fromStatus, PaymentStatus toStatus,
                         String reason, String correlationId, Instant changedAt) {
        this.payment = payment;
        this.sequenceNo = sequenceNo;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.correlationId = correlationId;
        this.changedAt = changedAt;
    }

    public int getSequenceNo() { return sequenceNo; }
    public PaymentStatus getFromStatus() { return fromStatus; }
    public PaymentStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public String getCorrelationId() { return correlationId; }
    public Instant getChangedAt() { return changedAt; }
}
