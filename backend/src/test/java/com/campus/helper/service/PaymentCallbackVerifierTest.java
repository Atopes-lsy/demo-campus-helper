package com.campus.helper.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class PaymentCallbackVerifierTest {

    private static final String SECRET = "test-payment-callback-secret";
    private static final long MAX_CALLBACK_AGE_SECONDS = Duration.ofMinutes(5).toSeconds();

    private final PaymentCallbackVerifier verifier = new PaymentCallbackVerifier(SECRET);

    @Test
    void acceptsValidSignatureAndRejectsModifiedPayload() throws Exception {
        long now = Instant.now().getEpochSecond();
        String signature = sign("PAY001:TX001:true:" + now);

        assertTrue(verifier.verify("PAY001", "TX001", true, now, signature));
        assertFalse(verifier.verify("PAY001", "TX001", false, now, signature));
        assertFalse(verifier.verify("PAY001", "TX002", true, now, signature));
    }

    @Test
    void rejectsReplayOutsideTheAllowedWindow() throws Exception {
        long stale = Instant.now().getEpochSecond() - MAX_CALLBACK_AGE_SECONDS - 1;
        // Correctly signed for its own timestamp, but too old to be accepted now.
        String signature = sign("PAY001:TX001:true:" + stale);

        assertFalse(verifier.verify("PAY001", "TX001", true, stale, signature));
    }

    @Test
    void rejectsSignatureBoundToADifferentTimestamp() throws Exception {
        long now = Instant.now().getEpochSecond();
        String signatureForOtherTimestamp = sign("PAY001:TX001:true:" + (now - 60));

        // The timestamp is part of the signed content, so moving it into the window
        // invalidates the signature rather than extending its life.
        assertFalse(verifier.verify("PAY001", "TX001", true, now, signatureForOtherTimestamp));
    }

    @Test
    void rejectsMissingTimestamp() throws Exception {
        String signature = sign("PAY001:TX001:true:0");

        assertFalse(verifier.verify("PAY001", "TX001", true, null, signature));
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }
}
