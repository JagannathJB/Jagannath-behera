package com.finpilot.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical SHA-256 of a create request, used to detect an idempotency key reused with a different body. */
final class RequestHasher {

    private RequestHasher() {
    }

    static String hash(CreatePaymentRequest r) {
        String canonical = String.join("|",
                r.amount().stripTrailingZeros().toPlainString(),
                r.currency(),
                r.debtorAccount(),
                r.creditorAccount(),
                r.creditorName().trim(),
                r.description() == null ? "" : r.description().trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
