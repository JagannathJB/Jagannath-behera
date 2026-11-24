package com.finpilot.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Synthetic demo data for local development, enabled with SEED_ENABLED=true. Deterministic (fixed random seed),
 * idempotent (skips if seeded before) and goes through the domain model, so seeded payments obey the state machine.
 * It includes a few "hot" debtor accounts and just-below-10,000 amounts that later fraud rules can flag.
 */
@Component
@ConditionalOnProperty(name = "finpilot.seed.enabled", havingValue = "true")
class SeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);
    private static final String KEY_PREFIX = "seed-";
    private static final String CORRELATION_ID = "seed-run";
    private static final int BATCH_SIZE = 100;
    private static final int HOT_DEBTORS = 5;
    private static final int DEBTOR_POOL = 60;
    private static final int CREDITOR_POOL = 40;

    private static final String[] CURRENCIES = {"USD", "USD", "USD", "USD", "EUR", "EUR", "GBP", "GBP", "INR", "JPY"};
    private static final String[] CREDITOR_NAMES = {
        "Northwind Traders", "Acme Logistics Ltd", "Globex Supplies", "Initech Services", "Umbrella Pharma Distribution",
        "Stark Components", "Wayne Freight", "Hooli Cloud Hosting", "Vandelay Imports", "Soylent Foods", "Tyrell Robotics",
        "Cyberdyne Systems"};
    private static final String[] FAILURE_REASONS = {
        "Insufficient funds in debtor account", "Beneficiary account closed", "Downstream bank unavailable",
        "Sanctions screening timed out", "Invalid beneficiary account", "Daily limit exceeded"};

    private final PaymentRepository repository;
    private final PaymentReferenceGenerator references;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int count;

    SeedDataRunner(PaymentRepository repository, PaymentReferenceGenerator references, TransactionTemplate tx,
                   Clock clock, @Value("${finpilot.seed.count:500}") int count) {
        this.repository = repository;
        this.references = references;
        this.tx = tx;
        this.clock = clock;
        this.count = count;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.existsByIdempotencyKeyStartingWith(KEY_PREFIX)) {
            log.info("Seed data already present; skipping");
            return;
        }
        Random random = new Random(42);
        Instant now = clock.instant();
        List<Payment> batch = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            batch.add(buildPayment(i, random, now));
            if (batch.size() == BATCH_SIZE) {
                saveBatch(batch);
                batch = new ArrayList<>();
            }
        }
        saveBatch(batch);
        log.info("Seeded {} synthetic payments", count);
    }

    private void saveBatch(List<Payment> batch) {
        if (!batch.isEmpty()) {
            tx.executeWithoutResult(status -> repository.saveAll(batch));
        }
    }

    private Payment buildPayment(int index, Random random, Instant now) {
        Instant t = now.minus(Duration.ofSeconds((long) (random.nextDouble() * 30 * 24 * 3600)));
        boolean hot = random.nextInt(100) < 6;
        int debtorIndex = hot ? random.nextInt(HOT_DEBTORS) : HOT_DEBTORS + random.nextInt(DEBTOR_POOL - HOT_DEBTORS);
        int creditorIndex = random.nextInt(CREDITOR_POOL);

        var request = new CreatePaymentRequest(
                amount(random),
                CURRENCIES[random.nextInt(CURRENCIES.length)],
                account(debtorIndex),
                account(1000 + creditorIndex),
                CREDITOR_NAMES[creditorIndex % CREDITOR_NAMES.length],
                "Invoice INV-" + (10000 + random.nextInt(90000)));

        Payment payment = Payment.create(request, references.next(), KEY_PREFIX + String.format("%05d", index),
                RequestHasher.hash(request), CORRELATION_ID, t);

        t = t.plusSeconds(1);
        payment.transitionTo(PaymentStatus.VALIDATED, "Validation passed", CORRELATION_ID, t);
        int outcome = random.nextInt(100);
        if (outcome < 5) {
            return payment;
        }
        t = t.plusSeconds(2 + random.nextInt(5));
        payment.transitionTo(PaymentStatus.PROCESSING, "Submitted to payment rail", CORRELATION_ID, t);
        if (outcome < 10) {
            return payment;
        }
        t = t.plusSeconds(1 + random.nextInt(20));
        if (outcome < 78) {
            payment.transitionTo(PaymentStatus.COMPLETED, "Settled", CORRELATION_ID, t);
        } else if (outcome < 90) {
            payment.transitionTo(PaymentStatus.FAILED, FAILURE_REASONS[random.nextInt(FAILURE_REASONS.length)],
                    CORRELATION_ID, t);
        } else {
            payment.transitionTo(PaymentStatus.REQUIRES_REVIEW, "Flagged for manual review", CORRELATION_ID, t);
        }
        return payment;
    }

    /** Log-uniform between 20 and 25,000, with ~3% just below 10,000 (structuring-style amounts). */
    private static BigDecimal amount(Random random) {
        if (random.nextInt(100) < 3) {
            return BigDecimal.valueOf(9500 + random.nextInt(490)).add(BigDecimal.valueOf(random.nextInt(100), 2));
        }
        double value = 20 * Math.exp(random.nextDouble() * Math.log(25000.0 / 20.0));
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String account(int n) {
        return "SYN" + String.format("%012d", 100000000000L + n * 7919L);
    }
}
