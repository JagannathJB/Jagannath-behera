package com.finpilot.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack checks against a throwaway PostgreSQL: Flyway, JPA schema validation, idempotency (including a real
 * concurrent race), the state machine, and search. Skipped (not failed) when Docker is unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class PaymentsApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE payment_status_history, payments CASCADE");
    }

    private String body(String amount, String currency) {
        return """
                {"amount": %s, "currency": "%s", "debtorAccount": "SYN100000007919",
                 "creditorAccount": "SYN100000099999", "creditorName": "Northwind Traders", "description": "test"}
                """.formatted(amount, currency);
    }

    private ResponseEntity<String> post(String path, String key, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return mapper.readTree(response.getBody());
    }

    @Test
    void migrationsApplyAndEmptyDatabaseReturnsEmptyPage() throws Exception {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isEqualTo(2);

        JsonNode page = json(rest.getForEntity("/api/v1/payments", String.class));
        assertThat(page.get("content").isArray()).isTrue();
        assertThat(page.get("totalElements").asLong()).isZero();
    }

    @Test
    void healthEndpointReportsUp() {
        var response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void createValidatesAndStoresPaymentWithHistory() throws Exception {
        var response = post("/api/v1/payments", "create-test-0001", body("1250.50", "USD"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode created = json(response);
        assertThat(created.get("status").asText()).isEqualTo("VALIDATED");
        assertThat(created.get("paymentReference").asText()).startsWith("PAY-");
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();

        JsonNode detail = json(rest.getForEntity("/api/v1/payments/" + created.get("id").asText(), String.class));
        assertThat(detail.get("history")).hasSize(2);
        assertThat(detail.get("history").get(0).get("toStatus").asText()).isEqualTo("CREATED");
        assertThat(detail.get("history").get(1).get("toStatus").asText()).isEqualTo("VALIDATED");
        assertThat(detail.get("allowedTransitions")).extracting(JsonNode::asText)
                .containsExactly("PROCESSING", "REQUIRES_REVIEW", "FAILED");
    }

    @Test
    void sameKeyReplaysAndDifferentBodyIsRejected() throws Exception {
        var first = post("/api/v1/payments", "idem-test-00001", body("100.00", "USD"));
        var replay = post("/api/v1/payments", "idem-test-00001", body("100", "USD"));
        var conflict = post("/api/v1/payments", "idem-test-00001", body("999.00", "USD"));

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(json(replay).get("id").asText()).isEqualTo(json(first).get("id").asText());
        assertThat(conflict.getStatusCode().value()).isEqualTo(422);
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithOneKeyCreateExactlyOnePayment() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<ResponseEntity<String>> task = () -> {
                start.await();
                return post("/api/v1/payments", "race-test-00001", body("42.00", "EUR"));
            };
            futures.add(pool.submit(task));
        }
        start.countDown();

        int created = 0;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Future<ResponseEntity<String>> future : futures) {
            ResponseEntity<String> response = future.get(30, TimeUnit.SECONDS);
            assertThat(response.getStatusCode().value()).isIn(200, 201);
            if (response.getStatusCode().value() == 201) {
                created++;
            }
            ids.add(json(response).get("id").asText());
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(ids).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from payments where idempotency_key = 'race-test-00001'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidInputIsRejectedWithFieldErrorsAndMissingKeyWith400() throws Exception {
        var invalid = post("/api/v1/payments", "bad-input-0001", body("-1", "usd"));
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(json(invalid).get("errors").has("amount")).isTrue();

        assertThat(post("/api/v1/payments", null, body("10", "USD")).getStatusCode().value()).isEqualTo(400);
        assertThat(post("/api/v1/payments", "x", body("10", "USD")).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void businessRuleViolationCreatesFailedPayment() throws Exception {
        String sameAccount = """
                {"amount": 10, "currency": "USD", "debtorAccount": "SYN100000007919",
                 "creditorAccount": "SYN100000007919", "creditorName": "Self"}
                """;
        var response = post("/api/v1/payments", "same-acct-0001", sameAccount);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(json(response).get("status").asText()).isEqualTo("FAILED");
        assertThat(json(response).get("failureReason").asText()).contains("must differ");
    }

    @Test
    void stateMachineAllowsValidAndRejectsInvalidTransitions() throws Exception {
        String id = json(post("/api/v1/payments", "state-test-0001", body("75.00", "GBP"))).get("id").asText();
        String path = "/api/v1/payments/" + id + "/transitions";

        var processing = post(path, null, "{\"targetStatus\":\"PROCESSING\",\"reason\":\"Submitted\"}");
        assertThat(processing.getStatusCode().value()).isEqualTo(200);
        assertThat(json(processing).get("payment").get("status").asText()).isEqualTo("PROCESSING");

        assertThat(post(path, null, "{\"targetStatus\":\"CREATED\"}").getStatusCode().value()).isEqualTo(409);
        assertThat(post(path, null, "{\"targetStatus\":\"FAILED\"}").getStatusCode().value()).isEqualTo(409);

        var completed = post(path, null, "{\"targetStatus\":\"COMPLETED\",\"reason\":\"Settled\"}");
        assertThat(completed.getStatusCode().value()).isEqualTo(200);
        assertThat(json(completed).get("history")).hasSize(4);
        assertThat(post(path, null, "{\"targetStatus\":\"PROCESSING\"}").getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void searchFiltersSortsAndPaginates() throws Exception {
        post("/api/v1/payments", "search-test-001", body("10.00", "USD"));
        post("/api/v1/payments", "search-test-002", body("500.00", "EUR"));
        post("/api/v1/payments", "search-test-003", body("900.00", "EUR"));
        String failedId = json(post("/api/v1/payments", "search-test-004", body("20.00", "USD"))).get("id").asText();
        post("/api/v1/payments/" + failedId + "/transitions", null, "{\"targetStatus\":\"FAILED\",\"reason\":\"Test\"}");

        JsonNode eur = json(rest.getForEntity("/api/v1/payments?currency=EUR&minAmount=600", String.class));
        assertThat(eur.get("totalElements").asLong()).isEqualTo(1);
        assertThat(eur.get("content").get(0).get("amount").decimalValue()).isEqualByComparingTo(new BigDecimal("900"));

        JsonNode failed = json(rest.getForEntity("/api/v1/payments?status=FAILED", String.class));
        assertThat(failed.get("totalElements").asLong()).isEqualTo(1);

        JsonNode paged = json(rest.getForEntity("/api/v1/payments?size=2&page=1&sort=amount,asc", String.class));
        assertThat(paged.get("totalElements").asLong()).isEqualTo(4);
        assertThat(paged.get("totalPages").asInt()).isEqualTo(2);
        assertThat(paged.get("content")).hasSize(2);
        assertThat(paged.get("content").get(0).get("amount").decimalValue()).isEqualByComparingTo(new BigDecimal("500"));

        assertThat(rest.getForEntity("/api/v1/payments?sort=debtorAccount,asc", String.class).getStatusCode().value())
                .isEqualTo(400);
    }
}
