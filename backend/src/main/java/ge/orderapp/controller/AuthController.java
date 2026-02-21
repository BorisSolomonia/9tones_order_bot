package ge.orderapp.controller;

import ge.orderapp.dto.request.LoginRequest;
import ge.orderapp.dto.response.UserDto;
import ge.orderapp.security.AuthenticationService;
import ge.orderapp.security.SessionAuthFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authService;

    public AuthController(AuthenticationService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                      HttpServletRequest httpRequest,
                                                      HttpServletResponse httpResponse) {
        String ip = httpRequest.getRemoteAddr();
        AuthenticationService.LoginResult result = authService.login(request.username(), request.password(), ip);

        Cookie cookie = buildSessionCookie(result.sessionId(), -1, httpRequest.isSecure());
        httpResponse.addCookie(cookie);

        return ResponseEntity.ok(Map.of(
                "user", result.user(),
                "message", "Login successful"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = extractSessionId(request);
        authService.logout(sessionId);

        Cookie cookie = buildSessionCookie("", 0, request.isSecure());
        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        return ResponseEntity.ok(user);
    }

    private String extractSessionId(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("SESSION_ID".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private Cookie buildSessionCookie(String value, int maxAge, boolean secure) {
        Cookie cookie = new Cookie("SESSION_ID", value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
}
