package com.tradingsim.portfolio.controller;

import com.tradingsim.portfolio.dto.AdminPortfolioDtos.SymbolExposure;
import com.tradingsim.portfolio.dto.AdminPortfolioDtos.UserPortfolioSummary;
import com.tradingsim.portfolio.dto.PortfolioDtos.PortfolioResponse;
import com.tradingsim.portfolio.service.AdminPortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/portfolio")
@RequiredArgsConstructor
public class AdminPortfolioController {

    private final AdminPortfolioService adminPortfolioService;

    @GetMapping("/users")
    public ResponseEntity<List<UserPortfolioSummary>> getUserPortfolios() {
        return ResponseEntity.ok(adminPortfolioService.getUserPortfolioSummaries());
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<PortfolioResponse> getUserPortfolio(@PathVariable UUID userId) {
        return ResponseEntity.ok(adminPortfolioService.getUserPortfolio(userId));
    }

    @GetMapping("/exposure")
    public ResponseEntity<List<SymbolExposure>> getExposureBySymbol() {
        return ResponseEntity.ok(adminPortfolioService.getExposureBySymbol());
    }
}
