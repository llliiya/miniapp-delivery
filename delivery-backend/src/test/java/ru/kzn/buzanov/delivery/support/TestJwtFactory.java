package ru.kzn.buzanov.delivery.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public final class TestJwtFactory {

    public static final String SECRET = "test-jwt-secret-for-integration-tests-only";

    private TestJwtFactory() {
    }

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public static String accessToken(Long userId, UUID organizationId, Set<String> roles) {
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("organizationId", organizationId.toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)));
        if (roles != null && !roles.isEmpty()) {
            builder.claim("roles", String.join(",", roles));
        }
        return builder.signWith(signingKey()).compact();
    }
}
