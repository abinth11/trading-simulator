package com.tradingsim.portfolio.controller;

import com.tradingsim.portfolio.context.Context;
import com.tradingsim.portfolio.dto.PortfolioDtos.PortfolioResponse;
import com.tradingsim.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    // GET /api/v1/portfolio
    @GetMapping
    public ResponseEntity<PortfolioResponse> getPortfolio(Context context) {
        context.validateLoggedIn();
        return ResponseEntity.ok(portfolioService.getPortfolio(context.getUserId()));
    }
}
