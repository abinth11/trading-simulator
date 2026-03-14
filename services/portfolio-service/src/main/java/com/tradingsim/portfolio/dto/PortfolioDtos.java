package com.tradingsim.portfolio.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class PortfolioDtos {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioResponse {
        private UUID userId;
        private BigDecimal cashBalance;
        private BigDecimal totalMarketValue;
        private BigDecimal totalPortfolioValue;
        private BigDecimal totalUnrealizedPnl;
        private List<HoldingResponse> holdings;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingResponse {
        private String symbol;
        private BigDecimal quantity;
        private BigDecimal avgBuyPrice;
        private BigDecimal lastPrice;
        private BigDecimal marketValue;
        private BigDecimal unrealizedPnl;
        private BigDecimal unrealizedPnlPct;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
    }
}
