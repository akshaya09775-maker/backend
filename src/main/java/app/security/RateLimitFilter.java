package app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how often one IP address may call the public account endpoints (login, register,
 * password reset) within a one-minute window, which slows down password guessing and email abuse.
 * For a multi-instance deployment use a shared store such as Redis or the reverse proxy's limiter.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password");

    private record Window(long minute, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxPerMinute;

    public RateLimitFilter(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long minute = Instant.now().getEpochSecond() / 60;
        if (windows.size() > 20_000) windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        // getRemoteAddr() is the real client IP when server.forward-headers-strategy is enabled behind a proxy
        Window w = windows.merge(request.getRemoteAddr() + request.getRequestURI(), new Window(minute, 1),
                (old, one) -> old.minute() == minute ? new Window(minute, old.count() + 1) : one);
        if (w.count() > maxPerMinute) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getOutputStream().write(
                    "{\"status\":429,\"message\":\"Too many requests. Please wait a minute and try again\"}"
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
