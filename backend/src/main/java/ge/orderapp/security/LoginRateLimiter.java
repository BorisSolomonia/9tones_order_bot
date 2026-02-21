package ge.orderapp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    @Value("${app.security.login-rate.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.login-rate.window-seconds:300}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public boolean isAllowed(String ip) {
        String key = normalizeIp(ip);
        AttemptRecord record = attempts.get(key);
        if (record == null) return true;
        Instant now = Instant.now();
        if (record.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
            attempts.remove(key);
            return true;
        }
        return record.count < maxAttempts;
    }

    public void recordFailure(String ip) {
        String key = normalizeIp(ip);
        attempts.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
                return new AttemptRecord(now, 1);
            }
            return new AttemptRecord(existing.windowStart, existing.count + 1);
        });
    }

    public void reset(String ip) {
        attempts.remove(normalizeIp(ip));
    }

    private String normalizeIp(String ip) {
        return (ip == null || ip.isBlank()) ? "unknown" : ip.trim();
    }

    private record AttemptRecord(Instant windowStart, int count) {}
}
