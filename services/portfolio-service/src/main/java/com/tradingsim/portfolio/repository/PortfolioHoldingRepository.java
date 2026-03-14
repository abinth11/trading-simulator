package com.tradingsim.portfolio.repository;

import com.tradingsim.portfolio.entity.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, UUID> {

    List<PortfolioHolding> findByUserId(UUID userId);

    Optional<PortfolioHolding> findByUserIdAndSymbol(UUID userId, String symbol);
}
