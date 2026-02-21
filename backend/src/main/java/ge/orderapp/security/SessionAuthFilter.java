package ge.orderapp.security;

import ge.orderapp.dto.response.UserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final String SESSION_COOKIE = "SESSION_ID";
    private static final String CURRENT_USER_ATTR = "currentUser";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login"
    );

    private final SessionManager sessionManager;

    public SessionAuthFilter(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Allow public paths
        if (PUBLIC_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Allow actuator
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String sessionId = extractSessionId(request);
        UserDto user = sessionManager.getSession(sessionId);

        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
            return;
        }

        // Set user on request and MDC
        request.setAttribute(CURRENT_USER_ATTR, user);
        MDC.put("userId", user.userId());
        MDC.put("username", user.username());

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
            MDC.remove("username");
        }
    }

    private String extractSessionId(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static UserDto getCurrentUser(HttpServletRequest request) {
        return (UserDto) request.getAttribute(CURRENT_USER_ATTR);
    }
}
