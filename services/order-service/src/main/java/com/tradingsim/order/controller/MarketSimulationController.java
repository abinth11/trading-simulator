package com.tradingsim.order.controller;

import com.tradingsim.order.dto.MarketSimulationDtos.MarketSimulationStatusResponse;
import com.tradingsim.order.dto.MarketSimulationDtos.StartMarketSimulationRequest;
import com.tradingsim.order.service.MarketSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/simulation")
@RequiredArgsConstructor
public class MarketSimulationController {

    private final MarketSimulationService marketSimulationService;

    @GetMapping
    public ResponseEntity<MarketSimulationStatusResponse> getStatus() {
        return ResponseEntity.ok(marketSimulationService.getStatus());
    }

    @GetMapping("/symbols")
    public ResponseEntity<List<String>> getConfiguredSymbols() {
        return ResponseEntity.ok(marketSimulationService.getConfiguredSymbols());
    }

    @PostMapping("/start")
    public ResponseEntity<MarketSimulationStatusResponse> start(
            @RequestBody(required = false) StartMarketSimulationRequest request) {
        return ResponseEntity.ok(marketSimulationService.start(request));
    }

    @PostMapping("/stop")
    public ResponseEntity<MarketSimulationStatusResponse> stop() {
        return ResponseEntity.ok(marketSimulationService.stop());
    }
}
