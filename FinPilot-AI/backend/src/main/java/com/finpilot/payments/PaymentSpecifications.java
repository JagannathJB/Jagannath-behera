package com.finpilot.payments;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Builds the dynamic search query. All user input is bound as parameters, never concatenated into JPQL. */
final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    static Specification<Payment> matching(PaymentSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }
            if (c.currency() != null && !c.currency().isBlank()) {
                predicates.add(cb.equal(root.<String>get("currency"), c.currency().trim().toUpperCase(Locale.ROOT)));
            }
            if (c.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("amount"), c.minAmount()));
            }
            if (c.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("amount"), c.maxAmount()));
            }
            if (c.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), c.createdFrom()));
            }
            if (c.createdTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), c.createdTo()));
            }
            if (c.reference() != null && !c.reference().isBlank()) {
                String pattern = "%" + escapeLike(c.reference().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.<String>get("paymentReference")), pattern, '\\'));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
