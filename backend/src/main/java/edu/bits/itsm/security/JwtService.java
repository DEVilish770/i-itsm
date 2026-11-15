package edu.bits.itsm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Map;

/**
 * Issues and validates HMAC-signed JWTs. The secret and lifetime come from configuration; no secret is committed
 * to the repository. If none is configured, a random key is generated at startup.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${itsm.jwt.secret:}") String secret,
                      @Value("${itsm.jwt.ttl-minutes:480}") long ttlMinutes) {
        if (secret == null || secret.isBlank()) {
            // Random per-process key: safe by default, but tokens stop working when the backend restarts
            // (users simply sign in again). Set JWT_SECRET (at least 32 bytes) to keep tokens across restarts.
            byte[] random = new byte[64];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
            log.warn("No JWT secret configured (itsm.jwt.secret / JWT_SECRET): using a random key; tokens end when the backend restarts");
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        this.ttlMillis = ttlMinutes * 60_000;
    }

    public String issue(String username, String role, Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claims(Map.of("role", role, "uid", userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /** @throws io.jsonwebtoken.JwtException if invalid/expired */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
