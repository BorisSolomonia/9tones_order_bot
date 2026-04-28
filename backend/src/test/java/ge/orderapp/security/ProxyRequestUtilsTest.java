package ge.orderapp.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyRequestUtilsTest {

    @Test
    void clientIpUsesFirstForwardedForValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.12");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.12");

        assertEquals("203.0.113.10", ProxyRequestUtils.clientIp(request));
    }

    @Test
    void clientIpFallsBackToRealIpThenRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.12");
        request.addHeader("X-Real-IP", "203.0.113.11");

        assertEquals("203.0.113.11", ProxyRequestUtils.clientIp(request));
    }

    @Test
    void httpsDetectedFromForwardedProto() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");

        assertTrue(ProxyRequestUtils.isHttps(request));
    }
}
