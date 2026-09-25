package com.tradingsim.portfolio.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards /api/v1/admin/**: requires an access token with the ADMIN role.
 * Missing or invalid token → 401, valid token without ADMIN → 403.
 */
@Component
@RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {

    private final JwtVerifier jwtVerifier;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Context context = jwtVerifier.verifyBearer(request.getHeader("Authorization"));
        jwtVerifier.requireAdmin(context);
        return true;
    }
}
