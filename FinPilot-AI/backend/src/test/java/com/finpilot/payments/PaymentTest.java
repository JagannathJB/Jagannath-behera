package com.finpilot.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private Payment newPayment() {
        return Payment.create(PaymentTestData.request(), "PAY-00001000", "key-12345678", "hash", "corr-1", T0);
    }

    @Test
    void newPaymentStartsCreatedWithInitialHistoryEntry() {
        Payment payment = newPayment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.getStatusHistory()).hasSize(1);
        assertThat(payment.getStatusHistory().get(0).getFromStatus()).isNull();
        assertThat(payment.getStatusHistory().get(0).getToStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void happyPathReachesCompletedWithOrderedHistory() {
        Payment payment = newPayment();

        payment.transitionTo(PaymentStatus.VALIDATED, "Validation passed", "corr-1", T0.plusSeconds(1));
        payment.transitionTo(PaymentStatus.PROCESSING, "Submitted", "corr-1", T0.plusSeconds(2));
        payment.transitionTo(PaymentStatus.COMPLETED, "Settled", "corr-1", T0.plusSeconds(3));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getUpdatedAt()).isEqualTo(T0.plusSeconds(3));
        assertThat(payment.getStatusHistory()).extracting(PaymentStatusHistory::getSequenceNo)
                .containsExactly(1, 2, 3, 4);
        assertThat(payment.getStatusHistory()).extracting(PaymentStatusHistory::getToStatus)
                .containsExactly(PaymentStatus.CREATED, PaymentStatus.VALIDATED, PaymentStatus.PROCESSING,
                        PaymentStatus.COMPLETED);
    }

    @Test
    void invalidTransitionIsRejectedAndLeavesStateUnchanged() {
        Payment payment = newPayment();

        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.COMPLETED, "skip", "corr-1", T0))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("CREATED")
                .hasMessageContaining("COMPLETED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.getStatusHistory()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED"})
    void terminalStatesAllowNoFurtherTransitions(PaymentStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(terminal.canTransitionTo(target)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void noStateCanMoveToItselfOrBackToCreated(PaymentStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
        assertThat(status.canTransitionTo(PaymentStatus.CREATED)).isFalse();
    }

    @Test
    void failedRequiresAReasonAndRecordsIt() {
        Payment payment = newPayment();

        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.FAILED, "  ", "corr-1", T0))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("reason");

        payment.transitionTo(PaymentStatus.FAILED, "Beneficiary account closed", "corr-1", T0.plusSeconds(1));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Beneficiary account closed");
    }

    @Test
    void reviewCanResumeProcessing() {
        Payment payment = newPayment();
        payment.transitionTo(PaymentStatus.VALIDATED, "ok", "c", T0);
        payment.transitionTo(PaymentStatus.REQUIRES_REVIEW, "Large amount", "c", T0);
        payment.transitionTo(PaymentStatus.PROCESSING, "Approved by analyst", "c", T0);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }
}
