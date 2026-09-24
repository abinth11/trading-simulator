package com.tradingsim.portfolio.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class AdminPortfolioDtos {

    public record UserPortfolioSummary(
            UUID userId,
            String username,
            BigDecimal cashBalance,
            BigDecimal totalMarketValue,
            BigDecimal totalPortfolioValue,
            BigDecimal totalUnrealizedPnl,
            long holdingsCount
    ) {}

    public record SymbolExposure(
            String symbol,
            BigDecimal exposure
    ) {}
}
