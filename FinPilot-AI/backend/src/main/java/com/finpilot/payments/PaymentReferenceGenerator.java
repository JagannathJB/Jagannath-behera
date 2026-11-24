package com.finpilot.payments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Human-readable unique references (PAY-00001000, ...) backed by a database sequence. */
@Component
public class PaymentReferenceGenerator {

    private final JdbcTemplate jdbc;

    public PaymentReferenceGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        Long value = jdbc.queryForObject("select nextval('payment_reference_seq')", Long.class);
        return String.format("PAY-%08d", value);
    }
}
