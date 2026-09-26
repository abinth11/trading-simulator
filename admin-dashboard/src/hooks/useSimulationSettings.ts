import { useEffect, useState } from "react";
import type { MarketSimulationStatus, StartMarketSimulationRequest } from "../types";

/**
 * Simulator settings shared by the Trading and Symbols pages, kept above the router so they
 * survive switching tabs.
 */
export function useSimulationSettings(configuredSymbols: string[], status: MarketSimulationStatus | null) {
  const [intervalMs, setIntervalMs] = useState(3000);
  const [ordersPerTick, setOrdersPerTick] = useState(2);
  const [pool, setPool] = useState<string[]>([]);

  // Initialise the pool once configured symbols arrive; afterwards the user owns it
  useEffect(() => {
    if (pool.length === 0 && configuredSymbols.length > 0) {
      setPool(status?.symbols ?? configuredSymbols);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configuredSymbols]);

  const toggleSymbol = (symbol: string) =>
    setPool((current) => (current.includes(symbol) ? current.filter((s) => s !== symbol) : [...current, symbol]));

  const request: StartMarketSimulationRequest = { intervalMs, ordersPerTick, symbols: pool };

  return { intervalMs, setIntervalMs, ordersPerTick, setOrdersPerTick, pool, setPool, toggleSymbol, request };
}

export type SimulationSettings = ReturnType<typeof useSimulationSettings>;
