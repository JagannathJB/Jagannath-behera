package com.finpilot.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RequestHasherAndValidatorTest {

    private final PaymentValidator validator = new PaymentValidator(new BigDecimal("1000000"));

    @Test
    void hashIgnoresNumericScaleAndSurroundingWhitespace() {
        var a = new CreatePaymentRequest(new BigDecimal("100.00"), "USD", "SYN100000007919", "SYN100000099999", "Acme", null);
        var b = new CreatePaymentRequest(new BigDecimal("100"), "USD", "SYN100000007919", "SYN100000099999", " Acme ", "");

        assertThat(RequestHasher.hash(a)).isEqualTo(RequestHasher.hash(b));
    }

    @Test
    void hashChangesWhenAnyBusinessFieldChanges() {
        var base = PaymentTestData.request();
        var otherCurrency = new CreatePaymentRequest(base.amount(), "EUR", base.debtorAccount(),
                base.creditorAccount(), base.creditorName(), base.description());
        var otherAmount = new CreatePaymentRequest(new BigDecimal("1250.51"), base.currency(), base.debtorAccount(),
                base.creditorAccount(), base.creditorName(), base.description());

        assertThat(RequestHasher.hash(otherCurrency)).isNotEqualTo(RequestHasher.hash(base));
        assertThat(RequestHasher.hash(otherAmount)).isNotEqualTo(RequestHasher.hash(base));
    }

    @Test
    void validRequestHasNoViolation() {
        assertThat(validator.findViolation(PaymentTestData.request())).isEmpty();
    }

    @Test
    void sameDebtorAndCreditorIsAViolation() {
        var r = new CreatePaymentRequest(new BigDecimal("10"), "USD", "SYN100000007919", "SYN100000007919", "X", null);

        assertThat(validator.findViolation(r)).hasValueSatisfying(v -> assertThat(v).contains("must differ"));
    }

    @Test
    void amountAboveLimitIsAViolation() {
        var r = new CreatePaymentRequest(new BigDecimal("1000000.01"), "USD", "SYN100000007919", "SYN100000099999", "X", null);

        assertThat(validator.findViolation(r)).hasValueSatisfying(v -> assertThat(v).contains("limit"));
    }

    @Test
    void unknownCurrencyIsAViolation() {
        var r = new CreatePaymentRequest(new BigDecimal("10"), "ABC", "SYN100000007919", "SYN100000099999", "X", null);

        assertThat(validator.findViolation(r)).hasValueSatisfying(v -> assertThat(v).contains("currency"));
    }
}
