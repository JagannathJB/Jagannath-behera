package com.finpilot.payments;

import java.math.BigDecimal;

final class PaymentTestData {

    private PaymentTestData() {
    }

    static CreatePaymentRequest request() {
        return new CreatePaymentRequest(new BigDecimal("1250.50"), "USD", "SYN100000007919", "SYN100000099999",
                "Northwind Traders", "Invoice INV-1001");
    }
}
