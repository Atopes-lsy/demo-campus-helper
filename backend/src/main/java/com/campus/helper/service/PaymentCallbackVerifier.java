package com.campus.helper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Verifies payment gateway callbacks.
 *
 * <p>The timestamp is part of the signed content and must fall inside a short window, so a
 * captured callback cannot be replayed later. Without it the signature stays valid forever
 * and a single observed callback could be resent indefinitely.
 */
@Slf4j
@Component
public class PaymentCallbackVerifier {

    /** How far a callback's timestamp may drift from now, in either direction. */
    private static final Duration MAX_CALLBACK_AGE = Duration.ofMinutes(5);

    private final byte[] secret;

    public PaymentCallbackVerifier(@Value("${payment.callback-secret}") String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("PAYMENT_CALLBACK_SECRET must contain at least 16 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param timestamp epoch seconds the gateway generated the callback; signed, and required
     *                  to be within {@link #MAX_CALLBACK_AGE} of now
     */
    public boolean verify(String paymentNo, String transactionId, boolean success,
                          Long timestamp, String signature) {
        if (paymentNo == null || transactionId == null || signature == null || timestamp == null) {
            return false;
        }

        long age = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (age > MAX_CALLBACK_AGE.toSeconds()) {
            log.warn("[支付回调] 时间戳超出允许窗口: paymentNo={}, 偏差={}秒", paymentNo, age);
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String content = paymentNo + ":" + transactionId + ":" + success + ":" + timestamp;
            byte[] expected = HexFormat.of().formatHex(
                    mac.doFinal(content.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected,
                    signature.toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify payment callback", e);
        }
    }
}
