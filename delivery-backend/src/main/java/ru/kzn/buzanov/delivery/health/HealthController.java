package ru.kzn.buzanov.delivery.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean dbHealthy = false;
        try (var conn = dataSource.getConnection()) {
            dbHealthy = conn.isValid(2);
        } catch (SQLException ignored) {
            // leave dbHealthy false
        }
        return Map.of(
                "status", "ok",
                "service", "delivery-backend",
                "database", Map.of("healthy", dbHealthy));
    }
}
