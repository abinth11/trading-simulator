package com.tradingsim.portfolio.service;

import com.tradingsim.portfolio.dto.AdminPortfolioDtos.SymbolExposure;
import com.tradingsim.portfolio.dto.AdminPortfolioDtos.UserPortfolioSummary;
import com.tradingsim.portfolio.dto.PortfolioDtos.PortfolioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPortfolioService {

    private final JdbcTemplate jdbcTemplate;
    private final PortfolioService portfolioService;

    @Transactional(readOnly = true)
    public List<UserPortfolioSummary> getUserPortfolioSummaries() {
        return jdbcTemplate.query("""
                SELECT u.id AS user_id,
                       u.username,
                       u.cash_balance,
                       COALESCE(SUM(ph.quantity * s.base_price), 0) AS total_market_value,
                       COALESCE(SUM((s.base_price - ph.avg_buy_price) * ph.quantity), 0) AS total_unrealized_pnl,
                       COUNT(ph.id) FILTER (WHERE ph.quantity > 0) AS holdings_count
                FROM users u
                LEFT JOIN portfolio_holdings ph ON ph.user_id = u.id AND ph.quantity > 0
                LEFT JOIN symbols s ON s.ticker = ph.symbol
                GROUP BY u.id, u.username, u.cash_balance
                ORDER BY total_market_value DESC, u.username ASC
                """,
                (rs, rowNum) -> {
                    BigDecimal cashBalance = rs.getBigDecimal("cash_balance");
                    BigDecimal marketValue = rs.getBigDecimal("total_market_value");
                    BigDecimal unrealizedPnl = rs.getBigDecimal("total_unrealized_pnl");
                    return new UserPortfolioSummary(
                            UUID.fromString(rs.getString("user_id")),
                            rs.getString("username"),
                            cashBalance,
                            marketValue,
                            cashBalance.add(marketValue),
                            unrealizedPnl,
                            rs.getLong("holdings_count")
                    );
                }
        );
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getUserPortfolio(UUID userId) {
        return portfolioService.getPortfolio(userId);
    }

    @Transactional(readOnly = true)
    public List<SymbolExposure> getExposureBySymbol() {
        return jdbcTemplate.query("""
                SELECT ph.symbol,
                       COALESCE(SUM(ph.quantity * s.base_price), 0) AS exposure
                FROM portfolio_holdings ph
                JOIN symbols s ON s.ticker = ph.symbol
                WHERE ph.quantity > 0
                GROUP BY ph.symbol
                ORDER BY exposure DESC, ph.symbol ASC
                """,
                (rs, rowNum) -> new SymbolExposure(
                        rs.getString("symbol"),
                        rs.getBigDecimal("exposure")
                )
        );
    }
}
