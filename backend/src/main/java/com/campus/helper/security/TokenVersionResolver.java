package com.campus.helper.security;

/**
 * Supplies the current token version for an account.
 *
 * <p>A JWT is valid until it expires — the whole point of not storing sessions. Bumping an
 * account's version and comparing it against the {@code ver} claim is what restores the
 * ability to revoke: every token issued before the bump carries the old number and is
 * refused, without the server having to track individual tokens.
 *
 * <p>An interface rather than a concrete class so {@code JwtService} can be tested without
 * Redis, and so the "unavailable" case below is expressible.
 */
public interface TokenVersionResolver {

    /**
     * @return the account's current version; {@code null} when it cannot be determined
     *         (for example Redis is unreachable). Callers treat null as "cannot verify" and
     *         fall back to accepting the token — see {@link JwtService#verify(String)}.
     */
    Long currentVersion(String role, Long userId);
}
