package com.tradingsim.portfolio;

import com.tradingsim.portfolio.context.AdminAccessInterceptor;
import com.tradingsim.portfolio.context.JwtVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAccessTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String OTHER_SECRET = "5A7134743777217A25432A462D4A614E645267556B58703273357638792F423F";

    private AdminAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        JwtVerifier verifier = new JwtVerifier();
        ReflectionTestUtils.setField(verifier, "secret", SECRET);
        interceptor = new AdminAccessInterceptor(verifier);
    }

    private static String token(String secret, String role, String type) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        return Jwts.builder()
                .claims(role == null ? Map.of("type", type) : Map.of("role", role, "type", type))
                .subject("someone@example.com")
                .id(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private static HttpStatus statusOf(Runnable call) {
        try {
            call.run();
            return HttpStatus.OK;
        } catch (ResponseStatusException e) {
            return HttpStatus.valueOf(e.getStatusCode().value());
        }
    }

    private HttpStatus callAdminApi(String authorizationHeader) {
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/portfolio/users");
        if (authorizationHeader != null) request.addHeader("Authorization", authorizationHeader);
        return statusOf(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void adminApi_withoutToken_is401() {
        assertThat(callAdminApi(null)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminApi_withUserToken_is403() {
        assertThat(callAdminApi("Bearer " + token(SECRET, "USER", "ACCESS"))).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminApi_withAdminToken_isAllowed() {
        assertThat(callAdminApi("Bearer " + token(SECRET, "ADMIN", "ACCESS"))).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminApi_withRefreshToken_is401() {
        assertThat(callAdminApi("Bearer " + token(SECRET, null, "REFRESH"))).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminApi_withForgedToken_is401() {
        assertThat(callAdminApi("Bearer " + token(OTHER_SECRET, "ADMIN", "ACCESS"))).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void verifier_rejectsMalformedHeader() {
        JwtVerifier verifier = new JwtVerifier();
        ReflectionTestUtils.setField(verifier, "secret", SECRET);
        assertThatThrownBy(() -> verifier.verifyBearer("Token abc"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
