package com.campus.helper.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String HEADER = BASE64_URL.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper objectMapper;
    private final TokenVersionResolver tokenVersionResolver;
    private final byte[] secret;
    private final long expirationHours;

    public JwtService(ObjectMapper objectMapper,
                      TokenVersionResolver tokenVersionResolver,
                      @Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-hours:12}") long expirationHours) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        }
        this.objectMapper = objectMapper;
        this.tokenVersionResolver = tokenVersionResolver;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationHours = expirationHours;
    }

    /**
     * Issue a token bound to the account's current version.
     *
     * @param tokenVersion the value from {@link TokenVersionResolver}; a later bump makes this
     *                     token invalid, which is how an otherwise-unrevocable JWT is revoked
     */
    public TokenResult issue(Long userId, String role, String displayName, long tokenVersion) {
        Instant expiresAt = Instant.now().plus(expirationHours, ChronoUnit.HOURS);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(userId));
        claims.put("role", role);
        claims.put("name", displayName);
        claims.put("ver", tokenVersion);
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        try {
            String payload = BASE64_URL.encodeToString(objectMapper.writeValueAsBytes(claims));
            String content = HEADER + "." + payload;
            return new TokenResult(content + "." + sign(content), expiresAt.getEpochSecond());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to issue access token", e);
        }
    }

    public AuthenticatedUser verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token format");
            }
            String content = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(content).getBytes(StandardCharsets.US_ASCII),
                    parts[2].getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Invalid token signature");
            }
            Map<String, Object> claims = objectMapper.readValue(
                    BASE64_URL_DECODER.decode(parts[1]), new TypeReference<>() {});
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw new IllegalArgumentException("Token expired");
            }
            Long userId = Long.valueOf(String.valueOf(claims.get("sub")));
            String role = String.valueOf(claims.get("role"));

            requireNotRevoked(role, userId, claims);
            return new AuthenticatedUser(userId, role, String.valueOf(claims.get("name")));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    /**
     * Reject a token whose {@code ver} no longer matches the account's current version —
     * the signature and expiry both still hold, but the token has been revoked.
     *
     * <p>Tokens issued before versioning existed carry no {@code ver} claim and are treated
     * as version 0, which is what an account with no recorded bump also reports. A null
     * current version means it could not be read at all, and the check is skipped rather
     * than locking everyone out (see {@link TokenVersionService#currentVersion}).
     */
    private void requireNotRevoked(String role, Long userId, Map<String, Object> claims) {
        Long current = tokenVersionResolver.currentVersion(role, userId);
        if (current == null) {
            return;
        }
        long tokenVersion = claims.get("ver") == null
                ? 0L : ((Number) claims.get("ver")).longValue();
        if (current != tokenVersion) {
            throw new IllegalArgumentException("Token revoked");
        }
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return BASE64_URL.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.US_ASCII)));
    }

    public record TokenResult(String token, long expiresAt) {
    }
}
