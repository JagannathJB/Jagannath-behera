package com.finpilot.payments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String VALID_BODY = """
            {"amount": 1250.50, "currency": "USD", "debtorAccount": "SYN100000007919",
             "creditorAccount": "SYN100000099999", "creditorName": "Northwind Traders", "description": "Invoice INV-1001"}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PaymentService paymentService;

    private PaymentResponse response(PaymentStatus status) {
        return new PaymentResponse(ID, "PAY-00001000", new BigDecimal("1250.50"), "USD", status,
                "SYN100000007919", "SYN100000099999", "Northwind Traders", "Invoice INV-1001", null, "corr-1",
                Instant.parse("2026-01-15T10:15:30Z"), Instant.parse("2026-01-15T10:15:31Z"));
    }

    @Test
    void createReturns201WithLocationAndForwardsKeyAndCorrelationId() throws Exception {
        when(paymentService.create(eq("key-12345678"), any(CreatePaymentRequest.class), eq("corr-1")))
                .thenReturn(new CreatePaymentResult(response(PaymentStatus.VALIDATED), true));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "key-12345678")
                        .header("X-Correlation-Id", "corr-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/payments/" + ID))
                .andExpect(header().string("X-Correlation-Id", "corr-1"))
                .andExpect(jsonPath("$.paymentReference").value("PAY-00001000"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));

        verify(paymentService).create(eq("key-12345678"), any(CreatePaymentRequest.class), eq("corr-1"));
    }

    @Test
    void replayedRequestReturns200WithReplayHeader() throws Exception {
        when(paymentService.create(any(), any(), any()))
                .thenReturn(new CreatePaymentResult(response(PaymentStatus.VALIDATED), false));

        mockMvc.perform(post("/api/v1/payments").header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodyReturnsProblemDetailWithFieldErrors() throws Exception {
        String invalid = """
                {"amount": -5, "currency": "usd", "debtorAccount": "x", "creditorAccount": "SYN100000099999", "creditorName": ""}
                """;

        mockMvc.perform(post("/api/v1/payments").header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.currency").exists())
                .andExpect(jsonPath("$.errors.debtorAccount").exists())
                .andExpect(jsonPath("$.errors.creditorName").exists());
    }

    @Test
    void idempotencyKeyReuseWithDifferentBodyReturns422() throws Exception {
        when(paymentService.create(any(), any(), any())).thenThrow(new IdempotencyConflictException());

        mockMvc.perform(post("/api/v1/payments").header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency key reuse"));
    }

    @Test
    void searchReturnsPageEnvelope() throws Exception {
        when(paymentService.search(any(PaymentSearchCriteria.class), eq(0), eq(20), eq("createdAt,desc")))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void searchBindsFilters() throws Exception {
        when(paymentService.search(any(PaymentSearchCriteria.class), eq(1), eq(5), eq("amount,asc")))
                .thenReturn(new PageResponse<>(List.of(response(PaymentStatus.FAILED)), 1, 5, 6, 2));

        mockMvc.perform(get("/api/v1/payments")
                        .param("status", "FAILED").param("currency", "USD").param("minAmount", "100")
                        .param("createdFrom", "2026-01-01T00:00:00Z")
                        .param("page", "1").param("size", "5").param("sort", "amount,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(paymentService).search(
                new PaymentSearchCriteria(PaymentStatus.FAILED, "USD", new BigDecimal("100"), null,
                        Instant.parse("2026-01-01T00:00:00Z"), null, null),
                1, 5, "amount,asc");
    }

    @Test
    void unknownStatusFilterIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/payments").param("status", "BOGUS")).andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownPaymentReturns404() throws Exception {
        when(paymentService.getPayment(ID)).thenThrow(new PaymentNotFoundException(ID));

        mockMvc.perform(get("/api/v1/payments/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"));
    }

    @Test
    void invalidTransitionReturns409() throws Exception {
        when(paymentService.transition(eq(ID), eq(PaymentStatus.COMPLETED), any(), any()))
                .thenThrow(new InvalidPaymentTransitionException(PaymentStatus.CREATED, PaymentStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/payments/" + ID + "/transitions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetStatus\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid state transition"));
    }
}
