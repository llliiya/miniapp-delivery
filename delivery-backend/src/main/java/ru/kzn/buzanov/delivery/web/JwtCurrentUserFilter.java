package ru.kzn.buzanov.delivery.web;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtCurrentUserFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    @Value("${app.auth.jwt-secret:${JWT_SECRET:}}")
    private String jwtSecret;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        String secret = jwtSecret == null ? "" : jwtSecret.trim();
        if (secret.isEmpty()) {
            throw new IllegalStateException("JWT secret is not configured (app.auth.jwt-secret / JWT_SECRET)");
        }
        secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
            String context = request.getContextPath();
            if (context != null && !context.isEmpty() && path.startsWith(context)) {
                path = path.substring(context.length());
            }
        }
        if (path == null) {
            path = "";
        }
        // Internal S2S paths use X-Delivery-Service-Key, not JWT.
        return path.startsWith("/internal/") || path.equals("/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HEADER);
        if (authHeader == null || !authHeader.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
            return;
        }

        final CurrentUser currentUser;
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            if (Boolean.TRUE.equals(claims.get("refresh", Boolean.class))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh token cannot be used as access token");
                return;
            }
            Long userId = parseUserId(claims.getSubject());
            List<String> roles = parseRoles(claims);
            UUID organizationId = parseOrganizationId(claims.get("organizationId"));
            currentUser = new CurrentUser(userId, roles, organizationId);
        } catch (JwtException | IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
            return;
        }

        request.setAttribute(CurrentUserHolder.REQUEST_ATTRIBUTE, currentUser);
        filterChain.doFilter(request, response);
    }

    private static Long parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject is missing");
        }
        try {
            return Long.parseLong(subject.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JWT subject must be numeric account user id");
        }
    }

    private static List<String> parseRoles(Claims claims) {
        Object rolesClaim = claims.get("roles");
        List<String> roles = new ArrayList<>();
        if (rolesClaim instanceof String rolesStr && !rolesStr.isBlank()) {
            for (String role : rolesStr.split(",")) {
                String normalized = role.trim();
                if (!normalized.isEmpty()) {
                    roles.add(normalized);
                }
            }
        } else if (rolesClaim instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                String normalized = String.valueOf(item).trim();
                if (!normalized.isEmpty()) {
                    roles.add(normalized);
                }
            }
        }
        if (roles.isEmpty()) {
            Object roleClaim = claims.get("role");
            if (roleClaim != null) {
                String role = String.valueOf(roleClaim).trim();
                if (!role.isEmpty()) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    private static UUID parseOrganizationId(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT organizationId is invalid");
        }
    }
}
