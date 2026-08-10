package ru.kzn.buzanov.delivery.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protects {@code /internal/**} with shared service key header {@code X-Delivery-Service-Key}.
 * Does not require JWT; missing or wrong key → 401 application/problem+json.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DeliveryServiceKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Delivery-Service-Key";

    private final String configuredApiKey;
    private final ObjectMapper objectMapper;

    public DeliveryServiceKeyFilter(
            @Value("${app.service.api-key:}") String configuredApiKey,
            ObjectMapper objectMapper) {
        this.configuredApiKey = configuredApiKey == null ? "" : configuredApiKey.trim();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = servletPath(request);
        return !path.startsWith("/internal/") && !path.equals("/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!StringUtils.hasText(configuredApiKey)) {
            writeProblem(response, HttpStatus.UNAUTHORIZED, "AUTH_ACCESS_DENIED", "Service API key is not configured");
            return;
        }
        String provided = request.getHeader(HEADER);
        if (!StringUtils.hasText(provided)) {
            writeProblem(response, HttpStatus.UNAUTHORIZED, "AUTH_MISSING_TOKEN", "Missing X-Delivery-Service-Key");
            return;
        }
        if (!configuredApiKey.equals(provided.trim())) {
            writeProblem(response, HttpStatus.UNAUTHORIZED, "AUTH_ACCESS_DENIED", "Invalid service API key");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeProblem(HttpServletResponse response,
                              HttpStatus status,
                              String code,
                              String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static String servletPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
            String context = request.getContextPath();
            if (context != null && !context.isEmpty() && path.startsWith(context)) {
                path = path.substring(context.length());
            }
        }
        return path == null ? "" : path;
    }
}
