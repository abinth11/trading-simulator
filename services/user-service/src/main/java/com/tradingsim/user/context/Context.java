package com.tradingsim.user.context;

import lombok.Getter;
import lombok.Builder;

import java.util.UUID;

@Getter
@Builder
public class Context {
    private UUID userId;
    private String email;
    private String role;
    private String token;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public void validateLoggedIn() {
        if (userId == null) {
            throw new com.tradingsim.user.exception.UnauthorizedException("Not authenticated");
        }
    }

    public void validateAdmin() {
        if (!isAdmin()) {
            throw new com.tradingsim.user.exception.UnauthorizedException("Admin access required");
        }
    }
}