package com.tradingsim.order.context;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.util.UUID;

/**
 * Verifies access tokens issued by user-service and turns them into a {@link Context}.
 * Failures are thrown as 401 / 403 {@link ResponseStatusException}s.
 */
@Component
@Slf4j
public class JwtVerifier {

    public static final String ADMIN_ROLE = "ADMIN";

    @Value("${jwt.secret}")
    private String secret;

    /** Verifies an "Authorization: Bearer ..." header value. */
    public Context verifyBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }
        return verify(authorizationHeader.substring(7));
    }

    public Context verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if ("REFRESH".equals(claims.get("type"))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token not allowed here");
            }

            return Context.builder()
                    .userId(UUID.fromString(claims.getId()))
                    .email(claims.getSubject())
                    .role((String) claims.get("role"))
                    .build();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    /** Verifies the token and requires the ADMIN role. */
    public Context verifyAdmin(String token) {
        Context context = verify(token);
        requireAdmin(context);
        return context;
    }

    public void requireAdmin(Context context) {
        if (!ADMIN_ROLE.equals(context.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
