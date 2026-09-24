package com.tradingsim.user.dto;

import com.tradingsim.user.entity.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class AdminUserDtos {

    public record AdminUserResponse(
            UUID id,
            String email,
            String username,
            BigDecimal cashBalance,
            User.Role role,
            boolean isActive,
            Instant createdAt
    ) {
        public static AdminUserResponse from(User user) {
            return new AdminUserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    user.getCashBalance(),
                    user.getRole(),
                    user.isActive(),
                    user.getCreatedAt()
            );
        }
    }

    public record AdminUserSummaryResponse(
            long totalUsers,
            long activeUsers,
            long inactiveUsers,
            long adminUsers,
            long botUsers
    ) {}
}
