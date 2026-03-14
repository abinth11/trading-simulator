package com.tradingsim.matching.config;

import com.tradingsim.matching.engine.MatchingEngineRouter;
import com.tradingsim.matching.publisher.TradeEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchingEngineConfig {

    /**
     * Wires the TradeEventPublisher as the trade event handler for the router.
     * The router passes this Consumer to each SymbolEngine it creates.
     */
    @Bean
    public MatchingEngineRouter matchingEngineRouter(TradeEventPublisher publisher) {
        return new MatchingEngineRouter(publisher::publish);
    }
}
