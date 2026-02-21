package ge.orderapp.security;

import ge.orderapp.dto.response.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private final ConcurrentHashMap<String, UserDto> sessions = new ConcurrentHashMap<>();

    public String createSession(UserDto user) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, user);
        log.info("Session created for user: {}", user.username());
        return sessionId;
    }

    public UserDto getSession(String sessionId) {
        if (sessionId == null) return null;
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) {
            UserDto removed = sessions.remove(sessionId);
            if (removed != null) {
                log.info("Session removed for user: {}", removed.username());
            }
        }
    }

    public void removeAllSessionsForUser(String userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
