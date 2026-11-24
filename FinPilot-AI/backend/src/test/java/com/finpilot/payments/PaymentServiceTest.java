package com.finpilot.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.ArgumentCaptor;

class PaymentServiceTest {

    private static final String KEY = "key-12345678";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentReferenceGenerator references = mock(PaymentReferenceGenerator.class);
    private PaymentService service;

    @BeforeEach
    void setUp() {
        when(references.next()).thenReturn("PAY-00001000");
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new PaymentService(repository, references, new PaymentValidator(new BigDecimal("1000000")),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void newPaymentIsValidatedAndSaved() {
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

        CreatePaymentResult result = service.create(KEY, PaymentTestData.request(), "corr-1");

        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(result.payment().paymentReference()).isEqualTo("PAY-00001000");
        verify(repository).saveAndFlush(any(Payment.class));
    }

    @Test
    void businessRuleViolationProducesFailedPaymentWithReason() {
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        var sameAccount = new CreatePaymentRequest(new BigDecimal("10"), "USD", "SYN100000007919", "SYN100000007919", "X", null);

        CreatePaymentResult result = service.create(KEY, sameAccount, "corr-1");

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.payment().failureReason()).contains("must differ");
    }

    @Test
    void sameKeyAndSameBodyReplaysWithoutSaving() {
        Payment existing = Payment.create(PaymentTestData.request(), "PAY-00000999", KEY,
                RequestHasher.hash(PaymentTestData.request()), "corr-0", NOW);
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        CreatePaymentResult result = service.create(KEY, PaymentTestData.request(), "corr-1");

        assertThat(result.created()).isFalse();
        assertThat(result.payment().paymentReference()).isEqualTo("PAY-00000999");
        verify(repository, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        Payment existing = Payment.create(PaymentTestData.request(), "PAY-00000999", KEY,
                RequestHasher.hash(PaymentTestData.request()), "corr-0", NOW);
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));
        var different = new CreatePaymentRequest(new BigDecimal("999.00"), "USD", "SYN100000007919", "SYN100000099999", "X", null);

        assertThatThrownBy(() -> service.create(KEY, different, "corr-1"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void losingARaceReplaysTheWinnersPayment() {
        Payment winner = Payment.create(PaymentTestData.request(), "PAY-00000998", KEY,
                RequestHasher.hash(PaymentTestData.request()), "corr-0", NOW);
        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        when(repository.saveAndFlush(any(Payment.class))).thenThrow(new DataIntegrityViolationException("uq_payments_idempotency_key"));

        CreatePaymentResult result = service.create(KEY, PaymentTestData.request(), "corr-1");

        assertThat(result.created()).isFalse();
        assertThat(result.payment().paymentReference()).isEqualTo("PAY-00000998");
    }

    @Test
    void malformedIdempotencyKeyIsRejectedBeforeAnyDatabaseAccess() {
        assertThatThrownBy(() -> service.create("short", PaymentTestData.request(), "corr-1"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("has spaces in it", PaymentTestData.request(), "corr-1"))
                .isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).findByIdempotencyKey(any());
    }

    @Test
    void searchRejectsBadPagingAndRanges() {
        var criteria = new PaymentSearchCriteria(null, null, null, null, null, null, null);
        var inverted = new PaymentSearchCriteria(null, null, new BigDecimal("10"), new BigDecimal("5"), null, null, null);

        assertThatThrownBy(() -> service.search(criteria, -1, 20, "createdAt,desc")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.search(criteria, 0, 0, "createdAt,desc")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.search(criteria, 0, 101, "createdAt,desc")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.search(inverted, 0, 20, "createdAt,desc")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void searchOnlyAllowsWhitelistedSortFields() {
        var criteria = new PaymentSearchCriteria(null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.search(criteria, 0, 20, "debtorAccount,asc"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unsupported sort field");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchPassesPagingAndSortToTheRepository() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        PageResponse<PaymentResponse> response =
                service.search(new PaymentSearchCriteria(PaymentStatus.FAILED, null, null, null, null, null, null),
                        2, 25, "amount,asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(25);
        assertThat(captor.getValue().getSort().getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(response.content()).isEmpty();
    }
}
