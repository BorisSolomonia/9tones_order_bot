package ge.orderapp.security;

import jakarta.servlet.http.HttpServletRequest;

public final class ProxyRequestUtils {

    private ProxyRequestUtils() {
    }

    public static String clientIp(HttpServletRequest request) {
        String forwardedFor = firstHeaderValue(request.getHeader("X-Forwarded-For"));
        if (!forwardedFor.isBlank()) {
            return forwardedFor;
        }
        String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
        if (!realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    public static boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if ("https".equalsIgnoreCase(proto)) {
            return true;
        }
        String forwarded = request.getHeader("Forwarded");
        return forwarded != null && forwarded.toLowerCase().contains("proto=https");
    }

    private static String firstHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int comma = value.indexOf(',');
        String first = comma >= 0 ? value.substring(0, comma) : value;
        return first.trim();
    }
}
