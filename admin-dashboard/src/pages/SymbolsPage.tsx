import { useMemo, useState } from "react";
import Panel from "../components/Panel";
import { SimulatorControls, SimulatorHeader } from "../components/SimulatorControls";
import StatusBadge from "../components/StatusBadge";
import type { DashboardData } from "../hooks/useDashboardData";
import type { SimulationSettings } from "../hooks/useSimulationSettings";
import { formatCompactNumber, formatMoney, formatTime } from "../utils/format";

interface SymbolsPageProps {
  data: DashboardData;
  settings: SimulationSettings;
}

export default function SymbolsPage({ data, settings }: SymbolsPageProps) {
  const { state, simulationStatus, simulationLoading, startSimulation, stopSimulation } = data;
  const [search, setSearch] = useState("");
  const running = simulationStatus?.running ?? false;

  const filteredSymbols = useMemo(() =>
    state.configuredSymbols.filter((s) => s.toLowerCase().includes(search.toLowerCase())),
    [state.configuredSymbols, search]
  );

  return (
    <section className="dashboard-grid">
      <Panel
        title="Symbol Registry"
        subtitle="All configured Nifty 50 symbols — click any card to add or remove from the simulation pool"
        action={
          <input
            className="table-search"
            aria-label="Search symbols by ticker"
            placeholder="Search symbols"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
      >
        <div className="symbol-registry-stats">
          <span><strong>{state.configuredSymbols.length}</strong> configured</span>
          <span><strong>{state.activeSymbols.length}</strong> active books</span>
          <span><strong>{settings.pool.length}</strong> in pool</span>
        </div>
        <div className="symbol-registry-grid">
          {filteredSymbols.length ? filteredSymbols.map((symbol) => {
            const activity = state.symbolActivity.find((a) => a.symbol === symbol);
            const isActive = state.activeSymbols.includes(symbol);
            const inPool = settings.pool.includes(symbol);
            return (
              <button
                key={symbol}
                type="button"
                className={`symbol-registry-card${isActive ? " is-active" : ""}${inPool ? " in-pool" : ""}`}
                aria-pressed={inPool}
                aria-label={`${inPool ? "Remove" : "Add"} ${symbol} ${inPool ? "from" : "to"} simulation pool`}
                onClick={() => settings.toggleSymbol(symbol)}
              >
                <span className="symbol-registry-name">{symbol}</span>
                <StatusBadge value={isActive ? "ACTIVE" : "IDLE"} tone={isActive ? "positive" : "neutral"} />
                <div className="symbol-registry-meta">
                  <span>{activity ? `${formatCompactNumber(activity.orders)} orders` : "No orders yet"}</span>
                  <span>{activity ? formatMoney(activity.tradedNotional) : "—"}</span>
                </div>
              </button>
            );
          }) : <div className="empty-state">No configured symbols match this search.</div>}
        </div>
      </Panel>

      <Panel title="Simulation Pool" subtitle="Select symbols and control the market simulator">
        <SimulatorHeader status={simulationStatus} />

        <div className="pool-actions">
          <button
            className="filter-pill button-pill"
            type="button"
            disabled={running}
            onClick={() => settings.setPool([...state.configuredSymbols])}
          >
            Select All
          </button>
          <button
            className="filter-pill button-pill"
            type="button"
            disabled={running}
            onClick={() => settings.setPool([])}
          >
            Clear
          </button>
          <span className="pool-count">{settings.pool.length} selected</span>
        </div>

        <div className="symbol-pool-chips">
          {settings.pool.map((symbol) => (
            <span key={symbol} className="pool-chip">
              {symbol}
              <button
                type="button"
                disabled={running}
                aria-label={`Remove ${symbol} from simulation pool`}
                onClick={() => settings.toggleSymbol(symbol)}
              >
                ×
              </button>
            </span>
          ))}
          {settings.pool.length === 0 && (
            <span className="pool-empty">No symbols selected — click cards on the left to add</span>
          )}
        </div>

        <SimulatorControls
          settings={settings}
          status={simulationStatus}
          loading={simulationLoading}
          onStart={() => startSimulation(settings.request)}
          onStop={() => stopSimulation()}
          startBlocked={settings.pool.length === 0}
        />

        <div className="simulation-stats">
          <div><span>Ticks Executed</span><strong>{formatCompactNumber(simulationStatus?.ticksExecuted ?? 0)}</strong></div>
          <div><span>Generated Orders</span><strong>{formatCompactNumber(simulationStatus?.generatedOrders ?? 0)}</strong></div>
          <div><span>Successful</span><strong>{formatCompactNumber(simulationStatus?.successfulOrders ?? 0)}</strong></div>
          <div><span>Failed</span><strong>{formatCompactNumber(simulationStatus?.failedOrders ?? 0)}</strong></div>
          <div><span>Last Tick</span><strong>{simulationStatus?.lastTickAt ? formatTime(simulationStatus.lastTickAt) : "--:--:--"}</strong></div>
          <div><span>Last Error</span><strong>{simulationStatus?.lastError ?? "None"}</strong></div>
        </div>
      </Panel>
    </section>
  );
}
