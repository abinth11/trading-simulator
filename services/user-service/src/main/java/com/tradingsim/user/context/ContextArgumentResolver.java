package com.tradingsim.user.context;

import com.tradingsim.user.exception.UnauthorizedException;
import com.tradingsim.user.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtService jwtService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Context.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        String header = webRequest.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }

        String token = header.substring(7);

        if (!jwtService.isTokenValid(token)) {
            throw new UnauthorizedException("Token is invalid or expired");
        }

        if ("REFRESH".equals(jwtService.extractTokenType(token))) {
            throw new UnauthorizedException("Refresh token cannot be used for API access");
        }

        return Context.builder()
                .userId(UUID.fromString(jwtService.extractUserId(token)))
                .email(jwtService.extractEmail(token))
                .role(jwtService.extractRole(token))
                .token(token)
                .build();
    }
}