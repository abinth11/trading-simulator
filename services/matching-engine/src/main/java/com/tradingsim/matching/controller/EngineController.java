package com.tradingsim.matching.controller;

import com.tradingsim.matching.engine.MatchingEngineRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/engine")
@RequiredArgsConstructor
public class EngineController {

    private final MatchingEngineRouter router;

    @GetMapping("/symbols")
    public ResponseEntity<Set<String>> getActiveSymbols() {
        return ResponseEntity.ok(router.getActiveSymbols());
    }

    @GetMapping("/orderbook/{symbol}")
    public ResponseEntity<Map<String, Object>> getOrderBook(@PathVariable String symbol) {
        return router.getOrderBook(symbol.toUpperCase())
                .map(book -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("symbol",    book.getSymbol());
                    result.put("bestBid",   book.getBestBidPrice().orElse(BigDecimal.ZERO));
                    result.put("bestAsk",   book.getBestAskPrice().orElse(BigDecimal.ZERO));
                    result.put("buyDepth",  book.getBuyDepth());
                    result.put("sellDepth", book.getSellDepth());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}