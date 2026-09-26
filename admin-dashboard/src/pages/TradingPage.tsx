import { useMemo, useState } from "react";
import DataTable from "../components/DataTable";
import DistributionBar from "../components/DistributionBar";
import Panel from "../components/Panel";
import { SimulatorControls, SimulatorHeader } from "../components/SimulatorControls";
import StatusBadge from "../components/StatusBadge";
import type { DashboardData } from "../hooks/useDashboardData";
import type { SimulationSettings } from "../hooks/useSimulationSettings";
import type { AdminOrder, TableColumn } from "../types";
import { formatCompactNumber, formatMoney, formatTime, mapStatusTone } from "../utils/format";

type StatusFilter = "ALL" | "FILLED" | "OPEN" | "REJECTED";

// Statuses whose orders can still trade or hold reserved funds
const OPEN_STATUSES = new Set(["PENDING", "PARTIAL", "CANCELLING"]);

const orderColumns: TableColumn<AdminOrder>[] = [
  { key: "createdAt", label: "Time", render: (row) => formatTime(row.createdAt) },
  { key: "symbol", label: "Symbol" },
  { key: "username", label: "Trader", sortable: true },
  {
    key: "side",
    label: "Side",
    render: (row) => <StatusBadge value={row.side} tone={mapStatusTone(row.side)} />
  },
  {
    key: "status",
    label: "Status",
    render: (row) => <StatusBadge value={row.status} tone={mapStatusTone(row.status)} />
  },
  { key: "quantity", label: "Qty", align: "right", render: (row) => formatCompactNumber(row.quantity), sortable: true, sortValue: (row) => row.quantity },
  { key: "price", label: "Price", align: "right", render: (row) => formatMoney(Number(row.price ?? 0)), sortable: true, sortValue: (row) => Number(row.price ?? 0) }
];

function summarizeSymbols(symbols: string[]): string {
  if (symbols.length === 0) return "No symbols selected";
  const preview = symbols.slice(0, 3).join(" • ");
  return symbols.length > 3 ? `${preview} • +${symbols.length - 3} more` : preview;
}

interface TradingPageProps {
  data: DashboardData;
  settings: SimulationSettings;
}

export default function TradingPage({ data, settings }: TradingPageProps) {
  const { state, simulationStatus, simulationLoading, startSimulation, stopSimulation } = data;
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [search, setSearch] = useState("");

  const filteredOrders = useMemo(() => {
    const query = search.trim().toLowerCase();
    return state.recentOrders.filter((order) => {
      const matchesStatus = statusFilter === "ALL"
        || (statusFilter === "OPEN" ? OPEN_STATUSES.has(order.status) : order.status === statusFilter);
      const matchesSearch = !query || [order.symbol, order.username, order.status, order.side, order.orderType, order.id]
        .some((value) => value.toLowerCase().includes(query));
      return matchesStatus && matchesSearch;
    });
  }, [search, statusFilter, state.recentOrders]);

  return (
    <section className="dashboard-grid trading-tab-grid">
      <Panel
        title="Advanced Trading Blotter"
        subtitle="Search recent orders and narrow activity by lifecycle state"
      >
        <div className="blotter-toolbar">
          <input
            className="table-search blotter-search"
            type="search"
            aria-label="Search orders by symbol, trader, status, side, type, or ID"
            placeholder="Search orders"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <div className="filter-row" role="group" aria-label="Filter orders by status">
            {(["ALL", "FILLED", "OPEN", "REJECTED"] as const).map((status) => (
              <button
                key={status}
                className={`filter-pill button-pill ${statusFilter === status ? "active" : ""}`}
                onClick={() => setStatusFilter(status)}
                aria-pressed={statusFilter === status}
                type="button"
              >
                {status}
              </button>
            ))}
          </div>
          <span className="blotter-count" aria-live="polite">
            {filteredOrders.length} of {state.recentOrders.length} orders
          </span>
        </div>
        <DataTable
          columns={orderColumns}
          rows={filteredOrders}
          rowKey={(row) => row.id}
          emptyMessage="No orders match this search and status filter."
          scrollHint
        />
      </Panel>

      <Panel title="Execution Monitor" subtitle="Live fills, status distribution, and throughput for the execution workflow">
        <div className="simulation-controls">
          <SimulatorHeader status={simulationStatus} />

          <SimulatorControls
            settings={settings}
            status={simulationStatus}
            loading={simulationLoading}
            onStart={() => startSimulation(settings.request)}
            onStop={() => stopSimulation()}
          />

          <div className="simulation-stats">
            <div>
              <span>Symbols</span>
              <strong>{summarizeSymbols(simulationStatus?.symbols.length ? simulationStatus.symbols : settings.pool)}</strong>
            </div>
            <div>
              <span>Ticks Executed</span>
              <strong>{formatCompactNumber(simulationStatus?.ticksExecuted ?? 0)}</strong>
            </div>
            <div>
              <span>Generated Orders</span>
              <strong>{formatCompactNumber(simulationStatus?.generatedOrders ?? 0)}</strong>
            </div>
            <div>
              <span>Successful Orders</span>
              <strong>{formatCompactNumber(simulationStatus?.successfulOrders ?? 0)}</strong>
            </div>
            <div>
              <span>Last Tick</span>
              <strong>{simulationStatus?.lastTickAt ? formatTime(simulationStatus.lastTickAt) : "--:--:--"}</strong>
            </div>
            <div>
              <span>Last Error</span>
              <strong>{simulationStatus?.lastError ?? "None"}</strong>
            </div>
          </div>
        </div>

        <div className="risk-cards">
          <article>
            <span>Trades Today</span>
            <strong>{formatCompactNumber(state.orderSummary?.tradesToday ?? 0)}</strong>
          </article>
          <article>
            <span>Open Orders</span>
            <strong>{formatCompactNumber(state.orderSummary?.openOrders ?? 0)}</strong>
          </article>
          <article>
            <span>Rejected Orders</span>
            <strong>{formatCompactNumber(state.orderSummary?.rejectedOrders ?? 0)}</strong>
          </article>
        </div>
        <DistributionBar segments={state.orderStatusMix} />
        <div className="trade-tape">
          {state.recentTrades.length ? state.recentTrades.map((trade) => (
            <div key={trade.id} className="trade-tape-row">
              <div>
                <strong>{trade.symbol}</strong>
                <span>{formatTime(trade.executedAt)}</span>
              </div>
              <div>
                <strong>{formatMoney(trade.price)}</strong>
                <span>{formatCompactNumber(trade.quantity)} qty</span>
              </div>
            </div>
          )) : <div className="empty-state">No recent trades available.</div>}
        </div>
      </Panel>
    </section>
  );
}
