package ge.orderapp.security;

import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.dto.response.UserDto;
import ge.orderapp.exception.BadRequestException;
import ge.orderapp.exception.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final InMemoryStore store;
    private final PasswordService passwordService;
    private final SessionManager sessionManager;
    private final LoginRateLimiter rateLimiter;

    public AuthenticationService(InMemoryStore store, PasswordService passwordService,
                                  SessionManager sessionManager, LoginRateLimiter rateLimiter) {
        this.store = store;
        this.passwordService = passwordService;
        this.sessionManager = sessionManager;
        this.rateLimiter = rateLimiter;
    }

    public record LoginResult(String sessionId, UserDto user) {}

    public LoginResult login(String username, String password, String ip) {
        if (!rateLimiter.isAllowed(ip)) {
            log.warn("Rate limit exceeded for IP: {}", ip);
            throw new RateLimitException("Too many login attempts. Try again in 5 minutes.");
        }

        // Find user by username
        UserDto user = store.getUserByUsername(username);
        if (user == null) {
            rateLimiter.recordFailure(ip);
            log.warn("Login failed: user not found: {}", username);
            throw new BadRequestException("Invalid username or password");
        }

        if (!user.active()) {
            rateLimiter.recordFailure(ip);
            log.warn("Login failed: user deactivated: {}", username);
            throw new BadRequestException("Invalid username or password");
        }

        // Verify password
        String storedHash = store.getUserPasswordHash(user.userId());
        if (storedHash == null || !passwordService.matches(password, storedHash)) {
            rateLimiter.recordFailure(ip);
            log.warn("Login failed: wrong password for user: {}", username);
            throw new BadRequestException("Invalid username or password");
        }

        String sessionId = sessionManager.createSession(user);
        rateLimiter.reset(ip);
        log.info("User logged in: {} (role: {})", username, user.role());
        return new LoginResult(sessionId, user);
    }

    public void logout(String sessionId) {
        sessionManager.removeSession(sessionId);
    }
}
