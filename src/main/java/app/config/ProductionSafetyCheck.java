package app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/** In the "prod" profile the application refuses to start with development defaults. */
@Slf4j
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    private static final Set<String> WEAK_ADMIN = Set.of("admin123", "admin", "password", "changeme");

    private final String jwtSecret;
    private final String adminPassword;
    private final String dbPassword;
    private final String corsOrigins;

    public ProductionSafetyCheck(@Value("${app.jwt.secret}") String jwtSecret,
                                 @Value("${app.admin.password}") String adminPassword,
                                 @Value("${spring.datasource.password:}") String dbPassword,
                                 @Value("${app.cors.allowed-origins:}") String corsOrigins) {
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
        this.dbPassword = dbPassword;
        this.corsOrigins = corsOrigins;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (jwtSecret.startsWith("dev-only") || jwtSecret.startsWith("replace-with") || jwtSecret.length() < 32) {
            throw new IllegalStateException("Refusing to start: set JWT_SECRET to a long random value (32+ characters)");
        }
        if (WEAK_ADMIN.contains(adminPassword.toLowerCase()) || adminPassword.length() < 10) {
            throw new IllegalStateException("Refusing to start: set ADMIN_PASSWORD to a strong password (10+ characters)");
        }
        if (dbPassword.isBlank() || dbPassword.equals("manager")) {
            throw new IllegalStateException("Refusing to start: set DB_PASSWORD to a strong database password");
        }
        if (corsOrigins.contains("localhost") && !corsOrigins.contains("https://")) {
            log.warn("CORS_ORIGINS only lists localhost; set it to your public https:// address");
        }
    }
}
