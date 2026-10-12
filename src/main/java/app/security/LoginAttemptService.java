package app.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory brute-force protection: too many failed logins lock the email for a while. */
@Service
public class LoginAttemptService {

    private record Entry(int failures, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lock;

    public LoginAttemptService(@Value("${app.login.max-attempts:5}") int maxAttempts,
                               @Value("${app.login.lock-minutes:15}") long lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.lock = Duration.ofMinutes(lockMinutes);
    }

    public boolean isLocked(String key) {
        Entry e = attempts.get(key);
        if (e == null || e.lockedUntil() == null) return false;
        if (Instant.now().isAfter(e.lockedUntil())) {
            attempts.remove(key);
            return false;
        }
        return true;
    }

    public void failed(String key) {
        attempts.merge(key, new Entry(1, null), (old, one) -> {
            int n = old.failures() + 1;
            return new Entry(n, n >= maxAttempts ? Instant.now().plus(lock) : null);
        });
    }

    public void succeeded(String key) {
        attempts.remove(key);
    }
}
