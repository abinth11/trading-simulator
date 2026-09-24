package com.tradingsim.order.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Provides the NSE Nifty 50 symbol list for the market simulator.
 * Prices are not fetched from any external source — bots drive price discovery.
 * The static bootstrap prices are only used to seed bot portfolio holdings on first start.
 */
@Service
public class NseMarketDataService {

    // Nifty 50 constituent symbols (NSE) — symbols with special characters excluded (M&M, BAJAJ-AUTO)
    public static final List<String> NIFTY_50_SYMBOLS = List.of(
            "ADANIENT", "ADANIPORTS", "APOLLOHOSP", "ASIANPAINT", "AXISBANK",
            "BAJAJFINSV", "BAJFINANCE", "BHARTIARTL", "BPCL", "BRITANNIA",
            "CIPLA", "COALINDIA", "DIVISLAB", "DRREDDY", "EICHERMOT",
            "GRASIM", "HCLTECH", "HDFCBANK", "HDFCLIFE", "HEROMOTOCO",
            "HINDALCO", "HINDUNILVR", "ICICIBANK", "INDUSINDBK", "INFY",
            "ITC", "JSWSTEEL", "KOTAKBANK", "LT", "LTIM",
            "MARUTI", "NESTLEIND", "NTPC", "ONGC", "POWERGRID",
            "RELIANCE", "SBILIFE", "SBIN", "SUNPHARMA", "TATAMOTORS",
            "TATACONSUM", "TATASTEEL", "TECHM", "TITAN", "TRENT",
            "TCS", "ULTRACEMCO", "WIPRO"
    );

    // Seed prices (INR) used only to bootstrap bot portfolio holdings on first simulation start.
    // These are approximate reference values — bots will move prices from here.
    private static final Map<String, BigDecimal> BOOTSTRAP_PRICES = Map.ofEntries(
            Map.entry("ADANIENT",   new BigDecimal("2450.00")),
            Map.entry("ADANIPORTS", new BigDecimal("1280.00")),
            Map.entry("APOLLOHOSP", new BigDecimal("6800.00")),
            Map.entry("ASIANPAINT", new BigDecimal("2350.00")),
            Map.entry("AXISBANK",   new BigDecimal("1245.00")),
            Map.entry("BAJAJFINSV", new BigDecimal("1620.00")),
            Map.entry("BAJFINANCE", new BigDecimal("7200.00")),
            Map.entry("BHARTIARTL", new BigDecimal("1890.00")),
            Map.entry("BPCL",       new BigDecimal("310.00")),
            Map.entry("BRITANNIA",  new BigDecimal("5100.00")),
            Map.entry("CIPLA",      new BigDecimal("1560.00")),
            Map.entry("COALINDIA",  new BigDecimal("485.00")),
            Map.entry("DIVISLAB",   new BigDecimal("5400.00")),
            Map.entry("DRREDDY",    new BigDecimal("6200.00")),
            Map.entry("EICHERMOT",  new BigDecimal("4900.00")),
            Map.entry("GRASIM",     new BigDecimal("2700.00")),
            Map.entry("HCLTECH",    new BigDecimal("1680.00")),
            Map.entry("HDFCBANK",   new BigDecimal("1621.75")),
            Map.entry("HDFCLIFE",   new BigDecimal("720.00")),
            Map.entry("HEROMOTOCO", new BigDecimal("4750.00")),
            Map.entry("HINDALCO",   new BigDecimal("680.00")),
            Map.entry("HINDUNILVR", new BigDecimal("2450.00")),
            Map.entry("ICICIBANK",  new BigDecimal("1285.00")),
            Map.entry("INDUSINDBK", new BigDecimal("1050.00")),
            Map.entry("INFY",       new BigDecimal("1782.80")),
            Map.entry("ITC",        new BigDecimal("465.00")),
            Map.entry("JSWSTEEL",   new BigDecimal("920.00")),
            Map.entry("KOTAKBANK",  new BigDecimal("1920.00")),
            Map.entry("LT",         new BigDecimal("3750.00")),
            Map.entry("LTIM",       new BigDecimal("5800.00")),
            Map.entry("MARUTI",     new BigDecimal("12400.00")),
            Map.entry("NESTLEIND",  new BigDecimal("2280.00")),
            Map.entry("NTPC",       new BigDecimal("375.00")),
            Map.entry("ONGC",       new BigDecimal("275.00")),
            Map.entry("POWERGRID",  new BigDecimal("335.00")),
            Map.entry("RELIANCE",   new BigDecimal("2892.50")),
            Map.entry("SBILIFE",    new BigDecimal("1650.00")),
            Map.entry("SBIN",       new BigDecimal("852.00")),
            Map.entry("SUNPHARMA",  new BigDecimal("1820.00")),
            Map.entry("TATAMOTORS", new BigDecimal("960.00")),
            Map.entry("TATACONSUM", new BigDecimal("1120.00")),
            Map.entry("TATASTEEL",  new BigDecimal("165.00")),
            Map.entry("TECHM",      new BigDecimal("1580.00")),
            Map.entry("TITAN",      new BigDecimal("3550.00")),
            Map.entry("TRENT",      new BigDecimal("5200.00")),
            Map.entry("TCS",        new BigDecimal("3918.00")),
            Map.entry("ULTRACEMCO", new BigDecimal("11200.00")),
            Map.entry("WIPRO",      new BigDecimal("482.10"))
    );

    /** Returns the full Nifty 50 symbol list. */
    public List<String> getSymbols() {
        return NIFTY_50_SYMBOLS;
    }

    /**
     * Returns the bootstrap seed price for a symbol.
     * Used only to initialise bot portfolio holdings before the first simulated trade.
     */
    public BigDecimal getBootstrapPrice(String symbol) {
        return BOOTSTRAP_PRICES.getOrDefault(symbol, new BigDecimal("100.00"));
    }
}
