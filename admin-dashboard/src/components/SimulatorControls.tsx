import type { SimulationSettings } from "../hooks/useSimulationSettings";
import type { MarketSimulationStatus } from "../types";
import StatusBadge from "./StatusBadge";

export function SimulatorHeader({ status }: { status: MarketSimulationStatus | null }) {
  return (
    <div className="simulation-header">
      <div>
        <span className="simulation-label">Market Simulator</span>
        <strong>{status?.running ? "Running" : "Stopped"}</strong>
      </div>
      <StatusBadge value={status?.running ? "LIVE" : "OFF"} tone={status?.running ? "positive" : "warning"} />
    </div>
  );
}

interface SimulatorControlsProps {
  settings: SimulationSettings;
  status: MarketSimulationStatus | null;
  loading: boolean;
  onStart: () => void;
  onStop: () => void;
  // Extra condition that blocks starting, e.g. an empty symbol pool
  startBlocked?: boolean;
}

/** Tick interval and orders-per-tick settings with Start / Stop buttons. */
export function SimulatorControls({ settings, status, loading, onStart, onStop, startBlocked = false }: SimulatorControlsProps) {
  const running = status?.running ?? false;

  return (
    <>
      <div className="simulation-form">
        <label className="simulation-field">
          <span>Tick Interval</span>
          <select
            value={settings.intervalMs}
            onChange={(event) => settings.setIntervalMs(Number(event.target.value))}
            disabled={running || loading}
          >
            <option value={1500}>1.5 sec</option>
            <option value={3000}>3 sec</option>
            <option value={5000}>5 sec</option>
          </select>
        </label>

        <label className="simulation-field">
          <span>Orders / Tick</span>
          <select
            value={settings.ordersPerTick}
            onChange={(event) => settings.setOrdersPerTick(Number(event.target.value))}
            disabled={running || loading}
          >
            <option value={2}>2</option>
            <option value={4}>4</option>
            <option value={6}>6</option>
          </select>
        </label>
      </div>

      <div className="simulation-actions">
        <button className="primary-button" type="button" disabled={running || loading || startBlocked} onClick={onStart}>
          Start Market
        </button>
        <button className="ghost-button" type="button" disabled={!running || loading} onClick={onStop}>
          Stop Market
        </button>
      </div>
    </>
  );
}
