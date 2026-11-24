package com.finpilot.payments;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {

    static final int MAX_PAGE_SIZE = 100;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_.:-]{8,100}$");
    private static final Set<String> SORTABLE = Set.of("createdAt", "amount", "status", "paymentReference");

    private final PaymentRepository repository;
    private final PaymentReferenceGenerator references;
    private final PaymentValidator validator;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentService(PaymentRepository repository, PaymentReferenceGenerator references,
                          PaymentValidator validator, TransactionTemplate tx, Clock clock) {
        this.repository = repository;
        this.references = references;
        this.validator = validator;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Idempotent create. Same key + same body returns the original payment; same key + different body is
     * rejected. Concurrent requests with one key are serialised by the unique constraint on idempotency_key:
     * the loser's insert fails and it replays the winner's payment.
     * Deliberately not @Transactional: the insert runs in its own transaction so a constraint violation
     * can be caught and handled after rollback.
     */
    public CreatePaymentResult create(String idempotencyKey, CreatePaymentRequest request, String correlationId) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new InvalidRequestException(
                    "Idempotency-Key must be 8-100 characters from A-Z a-z 0-9 _ . : -");
        }
        String hash = RequestHasher.hash(request);
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> replay(existing, hash))
                .orElseGet(() -> insert(idempotencyKey, hash, request, correlationId));
    }

    private CreatePaymentResult replay(Payment existing, String hash) {
        if (!hash.equals(existing.getRequestHash())) {
            throw new IdempotencyConflictException();
        }
        return new CreatePaymentResult(PaymentResponse.from(existing), false);
    }

    private CreatePaymentResult insert(String idempotencyKey, String hash, CreatePaymentRequest request,
                                       String correlationId) {
        try {
            Payment saved = tx.execute(status -> {
                Instant now = clock.instant();
                Payment payment = Payment.create(request, references.next(), idempotencyKey, hash, correlationId, now);
                Optional<String> violation = validator.findViolation(request);
                if (violation.isPresent()) {
                    payment.transitionTo(PaymentStatus.FAILED, violation.get(), correlationId, now);
                } else {
                    payment.transitionTo(PaymentStatus.VALIDATED, "Validation passed", correlationId, now);
                }
                return repository.saveAndFlush(payment);
            });
            return new CreatePaymentResult(PaymentResponse.from(saved), true);
        } catch (DataIntegrityViolationException e) {
            Payment winner = repository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return replay(winner, hash);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(PaymentSearchCriteria criteria, int page, int size, String sort) {
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (criteria.minAmount() != null && criteria.maxAmount() != null
                && criteria.minAmount().compareTo(criteria.maxAmount()) > 0) {
            throw new InvalidRequestException("minAmount must not exceed maxAmount");
        }
        if (criteria.createdFrom() != null && criteria.createdTo() != null
                && criteria.createdFrom().isAfter(criteria.createdTo())) {
            throw new InvalidRequestException("createdFrom must not be after createdTo");
        }
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        Page<PaymentResponse> result =
                repository.findAll(PaymentSpecifications.matching(criteria), pageable).map(PaymentResponse::from);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getPayment(UUID id) {
        return repository.findWithHistoryById(id)
                .map(PaymentDetailResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    @Transactional
    public PaymentDetailResponse transition(UUID id, PaymentStatus target, String reason, String correlationId) {
        Payment payment = repository.findWithHistoryById(id).orElseThrow(() -> new PaymentNotFoundException(id));
        payment.transitionTo(target, reason, correlationId, clock.instant());
        return PaymentDetailResponse.from(repository.saveAndFlush(payment));
    }

    static Sort parseSort(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",");
        String field = parts.length > 0 ? parts[0].trim() : "createdAt";
        if (!SORTABLE.contains(field)) {
            throw new InvalidRequestException("Unsupported sort field '" + field + "'. Allowed: " + new TreeSet<>(SORTABLE));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            direction = switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
                case "asc" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw new InvalidRequestException("Sort direction must be asc or desc");
            };
        }
        return Sort.by(new Sort.Order(direction, field), Sort.Order.asc("id"));
    }
}
