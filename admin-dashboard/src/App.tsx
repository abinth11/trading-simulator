import { useEffect, useMemo, useState } from "react";
import { Navigate, NavLink, Route, Routes, useLocation } from "react-router-dom";
import DataTable from "./components/DataTable";
import DistributionBar from "./components/DistributionBar";
import HealthGauge from "./components/HealthGauge";
import KpiCard from "./components/KpiCard";
import MarketActivityChart from "./components/MarketActivityChart";
import OrderBookDepth from "./components/OrderBookDepth";
import Panel from "./components/Panel";
import StatusBadge from "./components/StatusBadge";
import UserDrawer from "./components/UserDrawer";
import { useDashboardData } from "./hooks/useDashboardData";
import { navigationItems, serviceEndpoints, type AdminOrder, type AdminUser, type NavigationTab, type SymbolActivityItem, type TableColumn } from "./types";
import { formatBytes, formatCompactNumber, formatDurationSeconds, formatMoney, formatPlainPercent, formatSignedMoney, formatTime, mapStatusTone } from "./utils/format";

function summarizeSymbols(symbols: string[]): string {
  if (symbols.length === 0) return "No symbols selected";
  const preview = symbols.slice(0, 3).join(" • ");
  return symbols.length > 3 ? `${preview} • +${symbols.length - 3} more` : preview;
}

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

const userColumns: TableColumn<AdminUser & { portfolioValue: number; totalUnrealizedPnl: number; holdingsCount: number }>[] = [
  { key: "username", label: "Username", sortable: true },
  { key: "role", label: "Role", className: "users-role", render: (row) => <StatusBadge value={row.role} tone={mapStatusTone(row.role)} /> },
  { key: "isActive", label: "Status", render: (row) => <StatusBadge value={row.isActive ? "ACTIVE" : "INACTIVE"} tone={row.isActive ? "positive" : "critical"} /> },
  { key: "cashBalance", label: "Cash", className: "users-cash", align: "right", render: (row) => formatMoney(row.cashBalance), sortable: true, sortValue: (row) => row.cashBalance },
  { key: "portfolioValue", label: "Portfolio", className: "users-portfolio", align: "right", render: (row) => formatMoney(row.portfolioValue), sortable: true, sortValue: (row) => row.portfolioValue },
  { key: "totalUnrealizedPnl", label: "PnL", className: "users-pnl", align: "right", render: (row) => <span className={row.totalUnrealizedPnl >= 0 ? "positive-text" : "negative-text"}>{formatSignedMoney(row.totalUnrealizedPnl)}</span>, sortable: true, sortValue: (row) => row.totalUnrealizedPnl }
];

export default function App() {
  const [theme, setTheme] = useState<"dark" | "light">(() =>
    document.documentElement.dataset.theme === "light" ? "light" : "dark"
  );
  const [orderStatusFilter, setOrderStatusFilter] = useState<"ALL" | "FILLED" | "OPEN" | "REJECTED">("ALL");
  const [orderSearch, setOrderSearch] = useState("");
  const [userSearch, setUserSearch] = useState("");
  const [simulationIntervalMs, setSimulationIntervalMs] = useState(3000);
  const [simulationOrdersPerTick, setSimulationOrdersPerTick] = useState(2);
  const [symbolSearch, setSymbolSearch] = useState("");
  const [simulationPool, setSimulationPool] = useState<string[]>([]);
  const location = useLocation();
  const {
    state,
    metrics,
    alerts,
    serviceCards,
    mergedUsers,
    selectedUserView,
    riskSnapshot,
    marketPulse,
    headlineNumbers,
    liveState,
    simulationStatus,
    simulationLoading,
    selectSymbol,
    selectUser,
    refreshDashboard,
    startSimulation,
    stopSimulation,
    serviceHealthSnapshots,
    serviceHealthState
  } = useDashboardData();

  const activeTab = useMemo<NavigationTab>(() => {
    const matched = navigationItems.find((item) =>
      item.path === "/"
        ? location.pathname === "/"
        : location.pathname.startsWith(item.path)
    );
    return matched?.id ?? "overview";
  }, [location.pathname]);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  useEffect(() => {
    if (!window.matchMedia("(max-width: 1180px)").matches) return;
    const nav = document.querySelector<HTMLElement>(".nav-list");
    const activeLink = nav?.querySelector<HTMLElement>(".nav-item.active");
    if (!activeLink || !nav) return;

    const navBounds = nav.getBoundingClientRect();
    const linkBounds = activeLink.getBoundingClientRect();
    if (linkBounds.left < navBounds.left) {
      nav.scrollLeft -= navBounds.left - linkBounds.left + 2;
    } else if (linkBounds.right > navBounds.right) {
      nav.scrollLeft += linkBounds.right - navBounds.right + 2;
    }
  }, [activeTab, location.pathname, state.pageLoading]);

  const filteredOrders = useMemo(() => {
    const query = orderSearch.trim().toLowerCase();
    return state.recentOrders.filter((order) => {
      const matchesStatus = orderStatusFilter === "ALL"
        || (orderStatusFilter === "OPEN"
          ? order.status === "PENDING" || order.status === "PARTIAL"
          : order.status === orderStatusFilter);
      const matchesSearch = !query || [order.symbol, order.username, order.status, order.side, order.orderType, order.id]
        .some((value) => value.toLowerCase().includes(query));
      return matchesStatus && matchesSearch;
    });
  }, [orderSearch, orderStatusFilter, state.recentOrders]);

  const filteredUsers = useMemo(() => {
    const query = userSearch.trim().toLowerCase();
    if (!query) return mergedUsers;
    return mergedUsers.filter((user) =>
      [user.username, user.email, user.role].some((value) => value.toLowerCase().includes(query))
    );
  }, [mergedUsers, userSearch]);

  const tabTitle = navigationItems.find((item) => item.id === activeTab)?.label ?? "Overview";
  const showGlobalMarketHeader = activeTab === "overview";
  const liveLabel = useMemo(() => {
    if (liveState.orderFeed === "live" && (activeTab === "orderbook" ? liveState.orderBook === "live" : true)) {
      return "Streaming";
    }

    if (liveState.orderFeed === "reconnecting" || liveState.orderBook === "reconnecting") {
      return "Reconnecting";
    }

    if (liveState.orderFeed === "error" || liveState.orderBook === "error") {
      return "Stream Error";
    }

    return "Syncing";
  }, [activeTab, liveState.orderBook, liveState.orderFeed]);
  const systemSummary = useMemo(() => {
    const healthy = serviceHealthSnapshots.filter((service) => service.status === "UP").length;
    const degraded = serviceHealthSnapshots.length - healthy;
    const highestCpu = serviceHealthSnapshots.reduce(
      (max, service) => Math.max(max, service.systemCpuUsagePct),
      0
    );
    const highestHeap = serviceHealthSnapshots.reduce((max, service) => {
      const heapPct = service.heapMaxBytes > 0 ? (service.heapUsedBytes / service.heapMaxBytes) * 100 : 0;
      return Math.max(max, heapPct);
    }, 0);

    return {
      healthy,
      degraded,
      highestCpu,
      highestHeap
    };
  }, [serviceHealthSnapshots]);
  // Initialise the pool once configured symbols arrive; afterwards the user owns it
  useEffect(() => {
    if (simulationPool.length === 0 && state.configuredSymbols.length > 0) {
      setSimulationPool(simulationStatus?.symbols ?? state.configuredSymbols);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.configuredSymbols]);

  const filteredSymbols = useMemo(() =>
    state.configuredSymbols.filter((s) =>
      s.toLowerCase().includes(symbolSearch.toLowerCase())
    ),
    [state.configuredSymbols, symbolSearch]
  );

  if (state.pageLoading && !state.lastUpdated) {
    return <div className="app-loading">Booting trading workstation...</div>;
  }

  if (state.pageError && !state.lastUpdated) {
    return <div className="app-loading">Dashboard load failed: {state.pageError}</div>;
  }

  const pageDescriptions: Record<NavigationTab, string> = {
    overview: "A live view of market activity, execution quality, platform health, and exposure.",
    trading: "Search recent orders, review execution activity, and control the market simulator.",
    orderbook: "Inspect live bid and ask depth across active matching-engine books.",
    symbols: "Manage configured symbols and choose which markets the simulator uses.",
    users: "Review account status, balances, portfolio value, and holdings.",
    risk: "Monitor exposure concentration and unrealized account performance.",
    system: "Review service health, resource usage, and dependency status."
  };

  const connectionTone = liveLabel === "Streaming"
    ? "is-live"
    : liveLabel === "Stream Error"
      ? "has-error"
      : liveLabel === "Reconnecting"
        ? "is-reconnecting"
        : "is-syncing";

  return (
    <div className="workspace-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <div className="brand-mark">TS</div>
          <div>
            <h1>Trading Simulator</h1>
            <p>Operator Workstation</p>
          </div>
        </div>

        <div className="nav-group-label">Navigation</div>
        <nav className="nav-list">
          {navigationItems.map((item, index) => (
            <NavLink
              key={item.id}
              className={({ isActive }) => `nav-item ${isActive || activeTab === item.id ? "active" : ""}`}
              to={item.path}
              end={item.path === "/"}
            >
              <span>{String(index + 1).padStart(2, "0")}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <Panel title="Connected Services" subtitle="Local proxy routes" className="sidebar-panel">
          <div className="endpoint-list">
            {Object.entries(serviceEndpoints).map(([key, value]) => (
              <div key={key}>
                <span>{key}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
        </Panel>
      </aside>

      <main className="workspace">
        <header className={`terminal-header ${activeTab === "system" ? "system-header" : ""}`}>
          <div>
            <div className="eyebrow">{activeTab === "system" ? "Infrastructure Command" : "Live Market Operations"}</div>
            <h2>{tabTitle}</h2>
            <p>{pageDescriptions[activeTab]}</p>
          </div>
          <div className="header-actions">
            <button
              className="ghost-button theme-toggle"
              type="button"
              aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}
              aria-pressed={theme === "light"}
              onClick={() => {
                const nextTheme = theme === "dark" ? "light" : "dark";
                setTheme(nextTheme);
                try {
                  localStorage.setItem("admin-dashboard-theme", nextTheme);
                } catch {
                  // Keep the selected theme for this session if storage is unavailable.
                }
              }}
            >
              <span aria-hidden="true">{theme === "dark" ? "☀" : "☾"}</span>
              {theme === "dark" ? "Light mode" : "Dark mode"}
            </button>
            <div className="refresh-chip">
              <span>{state.pageLoading ? "Refreshing" : "Updated"}</span>
              <strong>{state.lastUpdated ? formatTime(state.lastUpdated) : "--:--:--"}</strong>
            </div>
            <button className="ghost-button" onClick={() => refreshDashboard()} type="button" disabled={state.pageLoading}>
              {state.pageLoading ? "Refreshing…" : "Refresh"}
            </button>
            <span className={`connection-chip ${connectionTone}`} role="status" aria-live="polite">
              <span className="connection-dot" aria-hidden="true" />
              {liveLabel}
            </span>
          </div>
        </header>

        {state.pageError && state.lastUpdated ? (
          <div className="dashboard-notice error-notice" role="alert">
            Refresh failed: {state.pageError}. Showing the last successful update from {formatTime(state.lastUpdated)}.
          </div>
        ) : null}

        {activeTab === "system" && serviceHealthState === "error" && serviceHealthSnapshots.length > 0 ? (
          <div className="dashboard-notice warning-notice" role="status">
            System health could not be refreshed. The service details below are from the last successful check.
          </div>
        ) : null}

        {activeTab === "system" ? (
          <section className="system-topbar">
            <article>
              <span>Healthy Services</span>
              <strong>{serviceHealthSnapshots.length ? systemSummary.healthy : "—"}</strong>
            </article>
            <article>
              <span>Degraded Services</span>
              <strong>{serviceHealthSnapshots.length ? systemSummary.degraded : "—"}</strong>
            </article>
            <article>
              <span>Peak System CPU</span>
              <strong>{serviceHealthSnapshots.length ? formatPlainPercent(systemSummary.highestCpu) : "—"}</strong>
            </article>
            <article>
              <span>Peak Heap Usage</span>
              <strong>{serviceHealthSnapshots.length ? formatPlainPercent(systemSummary.highestHeap) : "—"}</strong>
            </article>
          </section>
        ) : null}

        {showGlobalMarketHeader ? (
          <>
            <section className="overview-summary" aria-label="Market and platform summary">
              <div className="overview-summary-group">
                <h3 className="overview-summary-heading">Market Pulse</h3>
                <div className="market-strip">
                  <article>
                    <span>Spread</span>
                    <strong>{formatMoney(marketPulse.spread)}</strong>
                  </article>
                  <article>
                    <span>Best Bid</span>
                    <strong>{formatMoney(marketPulse.bestBid)}</strong>
                  </article>
                  <article>
                    <span>Best Ask</span>
                    <strong>{formatMoney(marketPulse.bestAsk)}</strong>
                  </article>
                  <article>
                    <span>Buy Depth</span>
                    <strong>{formatCompactNumber(marketPulse.buyDepth)}</strong>
                  </article>
                  <article>
                    <span>Sell Depth</span>
                    <strong>{formatCompactNumber(marketPulse.sellDepth)}</strong>
                  </article>
                  <article>
                    <span>Flow</span>
                    <strong>{headlineNumbers.activeOrderFlow}</strong>
                  </article>
                </div>
              </div>

              <div className="overview-summary-group">
                <h3 className="overview-summary-heading">Platform Snapshot</h3>
                <div className="kpi-grid">
                  {metrics.map((metric) => (
                    <KpiCard key={metric.label} metric={metric} />
                  ))}
                </div>
              </div>
            </section>
          </>
        ) : null}

        <Routes>
          <Route path="/" element={
          <>
        <section className="dashboard-grid hero-grid">
          <Panel title="Market Activity" subtitle="Intraday orders, fills, and notional volume stitched into a compact trading chart">
            <MarketActivityChart data={state.orderTimeline} />
          </Panel>

          <Panel title="Control Signals" subtitle="Operational alerts and quick risk pulse">
            <div className="alert-list">
              {alerts.length ? alerts.map((alert) => (
                <article className={`alert-item ${alert.severity}`} key={alert.title}>
                  <div className="alert-row">
                    <strong>{alert.title}</strong>
                    <StatusBadge value={alert.severity.toUpperCase()} tone={alert.severity} />
                  </div>
                  <p>{alert.detail}</p>
                </article>
              )) : <div className="empty-state">No active control signals.</div>}
            </div>
            <div className="micro-stats">
              <div>
                <span>Gross Exposure</span>
                <strong>{headlineNumbers.totalExposure}</strong>
              </div>
              <div>
                <span>Users in Drawdown</span>
                <strong>{headlineNumbers.drawdownUsers}</strong>
              </div>
              <div>
                <span>Top Symbol by Notional</span>
                <strong>{marketPulse.topVolumeSymbol}</strong>
              </div>
            </div>
          </Panel>
        </section>

        <section className="dashboard-grid">
          <Panel title="Operations Snapshot" subtitle="Fast service readiness checks for the core platform stack">
            <div className="service-grid">
              {serviceCards.map((service) => (
                <article key={service.name} className="service-card">
                  <div className="alert-row">
                    <strong>{service.name}</strong>
                    <StatusBadge value={service.status} tone={service.statusTone} />
                  </div>
                  <span>Port {service.port}</span>
                  <p>{service.note}</p>
                </article>
              ))}
            </div>
          </Panel>

          <Panel title="Capital At Risk" subtitle="High-level exposure and drawdown read without leaving the overview">
            <div className="risk-cards">
              <article>
                <span>Gross Exposure</span>
                <strong>{formatMoney(riskSnapshot.grossExposure)}</strong>
              </article>
              <article>
                <span>Drawdown Users</span>
                <strong>{riskSnapshot.drawdownUsers}</strong>
              </article>
              <article>
                <span>Largest Exposure</span>
                <strong>{riskSnapshot.bestSymbol?.symbol ?? "N/A"}</strong>
              </article>
            </div>
            <div className="exposure-list">
              {state.exposureBySymbol.slice(0, 5).map((item) => {
                const maxExposure = Math.max(...state.exposureBySymbol.map((entry) => entry.exposure), 1);
                return (
                  <div key={item.symbol} className="exposure-row">
                    <span>{item.symbol}</span>
                    <div className="exposure-bar-wrap">
                      <div className="exposure-bar" style={{ width: `${(item.exposure / maxExposure) * 100}%` }} />
                    </div>
                    <strong>{formatMoney(item.exposure)}</strong>
                  </div>
                );
              })}
            </div>
          </Panel>
        </section>

        <section className="dashboard-grid">
          <Panel title="Market Leaders" subtitle="Cross-symbol snapshot of the most active names on the platform">
            <div className="leaderboard">
              {state.symbolActivity.length ? state.symbolActivity.slice(0, 6).map((item: SymbolActivityItem) => (
                <div key={item.symbol} className="leaderboard-row">
                  <div>
                    <strong>{item.symbol}</strong>
                    <span>{formatCompactNumber(item.orders)} orders</span>
                  </div>
                  <div>
                    <strong>{formatMoney(item.tradedNotional)}</strong>
                    <span>{formatCompactNumber(item.filledOrders)} filled</span>
                  </div>
                </div>
              )) : <div className="empty-state">No symbol activity yet.</div>}
            </div>
          </Panel>

          <Panel title="Watchlist Accounts" subtitle="Top accounts by unrealized performance for a quick operator scan">
            <div className="leaderboard">
              {mergedUsers.length ? [...mergedUsers]
                .sort((a, b) => Math.abs(b.totalUnrealizedPnl) - Math.abs(a.totalUnrealizedPnl))
                .slice(0, 6)
                .map((user) => (
                  <div key={user.id} className="leaderboard-row">
                    <div>
                      <strong>{user.username}</strong>
                      <span>{user.holdingsCount} holdings</span>
                    </div>
                    <div>
                      <strong>{formatMoney(user.portfolioValue)}</strong>
                      <span className={user.totalUnrealizedPnl >= 0 ? "positive-text" : "negative-text"}>
                        {formatSignedMoney(user.totalUnrealizedPnl)}
                      </span>
                    </div>
                  </div>
                )) : <div className="empty-state">No account data available.</div>}
            </div>
          </Panel>
        </section>
          </>
          } />

          <Route path="/trading" element={
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
                  value={orderSearch}
                  onChange={(event) => setOrderSearch(event.target.value)}
                />
                <div className="filter-row" role="group" aria-label="Filter orders by status">
                  {(["ALL", "FILLED", "OPEN", "REJECTED"] as const).map((status) => (
                    <button
                      key={status}
                      className={`filter-pill button-pill ${orderStatusFilter === status ? "active" : ""}`}
                      onClick={() => setOrderStatusFilter(status)}
                      aria-pressed={orderStatusFilter === status}
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
                <div className="simulation-header">
                  <div>
                    <span className="simulation-label">Market Simulator</span>
                    <strong>{simulationStatus?.running ? "Running" : "Stopped"}</strong>
                  </div>
                  <StatusBadge
                    value={simulationStatus?.running ? "LIVE" : "OFF"}
                    tone={simulationStatus?.running ? "positive" : "warning"}
                  />
                </div>

                <div className="simulation-form">
                  <label className="simulation-field">
                    <span>Tick Interval</span>
                    <select
                      value={simulationIntervalMs}
                      onChange={(event) => setSimulationIntervalMs(Number(event.target.value))}
                      disabled={simulationStatus?.running || simulationLoading}
                    >
                      <option value={1500}>1.5 sec</option>
                      <option value={3000}>3 sec</option>
                      <option value={5000}>5 sec</option>
                    </select>
                  </label>

                  <label className="simulation-field">
                    <span>Orders / Tick</span>
                    <select
                      value={simulationOrdersPerTick}
                      onChange={(event) => setSimulationOrdersPerTick(Number(event.target.value))}
                      disabled={simulationStatus?.running || simulationLoading}
                    >
                      <option value={2}>2</option>
                      <option value={4}>4</option>
                      <option value={6}>6</option>
                    </select>
                  </label>
                </div>

                <div className="simulation-actions">
                  <button
                    className="primary-button"
                    type="button"
                    disabled={simulationStatus?.running || simulationLoading}
                    onClick={() => startSimulation({
                      intervalMs: simulationIntervalMs,
                      ordersPerTick: simulationOrdersPerTick,
                      symbols: simulationPool
                    })}
                  >
                    Start Market
                  </button>
                  <button
                    className="ghost-button"
                    type="button"
                    disabled={!simulationStatus?.running || simulationLoading}
                    onClick={() => stopSimulation()}
                  >
                    Stop Market
                  </button>
                </div>

                <div className="simulation-stats">
                  <div>
                    <span>Symbols</span>
                    <strong>{summarizeSymbols(simulationStatus?.symbols.length ? simulationStatus.symbols : simulationPool)}</strong>
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
          } />

          <Route path="/orderbook" element={
          <section className="dashboard-grid">
            <Panel title="Depth Monitor" subtitle="Live order-book inspection for active in-memory symbols">
              <div className="symbol-tabs">
                {state.activeSymbols.map((symbol) => (
                  <button
                    key={symbol}
                    className={`symbol-tab ${state.selectedSymbol === symbol ? "active" : ""}`}
                    onClick={() => selectSymbol(symbol)}
                    type="button"
                  >
                    {symbol}
                  </button>
                ))}
              </div>
              <div className="orderbook-header">
                <article>
                  <span>Symbol</span>
                  <strong>{state.currentBook.symbol}</strong>
                </article>
                <article>
                  <span>Best Bid</span>
                  <strong>{formatMoney(state.currentBook.bestBid)}</strong>
                </article>
                <article>
                  <span>Best Ask</span>
                  <strong>{formatMoney(state.currentBook.bestAsk)}</strong>
                </article>
                <article>
                  <span>Depth</span>
                  <strong>{formatCompactNumber(state.currentBook.buyDepth + state.currentBook.sellDepth)}</strong>
                </article>
              </div>
              <OrderBookDepth book={state.currentBook} />
            </Panel>

            <Panel title="Liquidity Snapshot" subtitle="Depth-specific metrics for the selected symbol without cross-tab trading noise">
              <div className="risk-cards">
                <article>
                  <span>Selected Symbol</span>
                  <strong>{state.currentBook.symbol}</strong>
                </article>
                <article>
                  <span>Spread</span>
                  <strong>{formatMoney(state.currentBook.bestAsk - state.currentBook.bestBid)}</strong>
                </article>
                <article>
                  <span>Total Depth</span>
                  <strong>{formatCompactNumber(state.currentBook.buyDepth + state.currentBook.sellDepth)}</strong>
                </article>
              </div>
              <div className="detail-list">
                <div>
                  <span>Bid Side Orders</span>
                  <strong>{formatCompactNumber(state.currentBook.buyDepth)}</strong>
                </div>
                <div>
                  <span>Ask Side Orders</span>
                  <strong>{formatCompactNumber(state.currentBook.sellDepth)}</strong>
                </div>
                <div>
                  <span>Active Symbols</span>
                  <strong>{formatCompactNumber(state.activeSymbols.length)}</strong>
                </div>
                <div>
                  <span>Stream State</span>
                  <strong>{liveState.orderBook}</strong>
                </div>
              </div>
            </Panel>
          </section>
          } />

          <Route path="/symbols" element={
          <section className="dashboard-grid">
            <Panel
              title="Symbol Registry"
              subtitle="All configured Nifty 50 symbols — click any card to add or remove from the simulation pool"
              action={
                <input
                  className="table-search"
                  placeholder="Search symbols"
                  value={symbolSearch}
                  onChange={(e) => setSymbolSearch(e.target.value)}
                />
              }
            >
              <div className="symbol-registry-stats">
                <span><strong>{state.configuredSymbols.length}</strong> configured</span>
                <span><strong>{state.activeSymbols.length}</strong> active books</span>
                <span><strong>{simulationPool.length}</strong> in pool</span>
              </div>
              <div className="symbol-registry-grid">
                {filteredSymbols.length ? filteredSymbols.map((symbol) => {
                  const activity = state.symbolActivity.find((a) => a.symbol === symbol);
                  const isActive = state.activeSymbols.includes(symbol);
                  const inPool = simulationPool.includes(symbol);
                  return (
                    <button
                      key={symbol}
                      type="button"
                      className={`symbol-registry-card${isActive ? " is-active" : ""}${inPool ? " in-pool" : ""}`}
                      onClick={() =>
                        setSimulationPool((prev) =>
                          prev.includes(symbol) ? prev.filter((s) => s !== symbol) : [...prev, symbol]
                        )
                      }
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
              <div className="simulation-header">
                <div>
                  <span className="simulation-label">Market Simulator</span>
                  <strong>{simulationStatus?.running ? "Running" : "Stopped"}</strong>
                </div>
                <StatusBadge
                  value={simulationStatus?.running ? "LIVE" : "OFF"}
                  tone={simulationStatus?.running ? "positive" : "warning"}
                />
              </div>

              <div className="pool-actions">
                <button
                  className="filter-pill button-pill"
                  type="button"
                  disabled={simulationStatus?.running}
                  onClick={() => setSimulationPool([...state.configuredSymbols])}
                >
                  Select All
                </button>
                <button
                  className="filter-pill button-pill"
                  type="button"
                  disabled={simulationStatus?.running}
                  onClick={() => setSimulationPool([])}
                >
                  Clear
                </button>
                <span className="pool-count">{simulationPool.length} selected</span>
              </div>

              <div className="symbol-pool-chips">
                {simulationPool.map((symbol) => (
                  <span key={symbol} className="pool-chip">
                    {symbol}
                    <button
                      type="button"
                      disabled={simulationStatus?.running}
                      onClick={() => setSimulationPool((prev) => prev.filter((s) => s !== symbol))}
                    >
                      ×
                    </button>
                  </span>
                ))}
                {simulationPool.length === 0 && (
                  <span className="pool-empty">No symbols selected — click cards on the left to add</span>
                )}
              </div>

              <div className="simulation-form">
                <label className="simulation-field">
                  <span>Tick Interval</span>
                  <select
                    value={simulationIntervalMs}
                    onChange={(e) => setSimulationIntervalMs(Number(e.target.value))}
                    disabled={simulationStatus?.running || simulationLoading}
                  >
                    <option value={1500}>1.5 sec</option>
                    <option value={3000}>3 sec</option>
                    <option value={5000}>5 sec</option>
                  </select>
                </label>
                <label className="simulation-field">
                  <span>Orders / Tick</span>
                  <select
                    value={simulationOrdersPerTick}
                    onChange={(e) => setSimulationOrdersPerTick(Number(e.target.value))}
                    disabled={simulationStatus?.running || simulationLoading}
                  >
                    <option value={2}>2</option>
                    <option value={4}>4</option>
                    <option value={6}>6</option>
                  </select>
                </label>
              </div>

              <div className="simulation-actions">
                <button
                  className="primary-button"
                  type="button"
                  disabled={simulationStatus?.running || simulationLoading || simulationPool.length === 0}
                  onClick={() => startSimulation({
                    intervalMs: simulationIntervalMs,
                    ordersPerTick: simulationOrdersPerTick,
                    symbols: simulationPool
                  })}
                >
                  Start Market
                </button>
                <button
                  className="ghost-button"
                  type="button"
                  disabled={!simulationStatus?.running || simulationLoading}
                  onClick={() => stopSimulation()}
                >
                  Stop Market
                </button>
              </div>

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
          } />

          <Route path="/users" element={
          <section className="dashboard-grid users-tab-grid">
            <Panel
              title="User Registry"
              subtitle="Search accounts and select View to inspect portfolio and holdings"
              action={
                <input
                  className="table-search"
                  aria-label="Search users by username, email, or role"
                  placeholder="Search users"
                  value={userSearch}
                  onChange={(event) => setUserSearch(event.target.value)}
                />
              }
            >
              <DataTable
                columns={userColumns}
                rows={filteredUsers}
                rowKey={(row) => row.id}
                emptyMessage={userSearch.trim() ? "No users match this search." : "No user accounts are available."}
                rowAction={{ label: "View", onSelect: selectUser }}
              />
            </Panel>

            <Panel title="Account Pulse" subtitle="Quick read on account health and concentration">
              <div className="leaderboard">
                {filteredUsers.slice(0, 8).map((user) => (
                  <div key={user.id} className="leaderboard-row">
                    <div>
                      <strong>{user.username}</strong>
                      <span>{user.role}</span>
                    </div>
                    <div>
                      <strong>{formatMoney(user.portfolioValue)}</strong>
                      <span className={user.totalUnrealizedPnl >= 0 ? "positive-text" : "negative-text"}>
                        {formatSignedMoney(user.totalUnrealizedPnl)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </Panel>
          </section>
          } />

          <Route path="/risk" element={
          <section className="dashboard-grid">
            <Panel title="Risk Concentration" subtitle="Exposure and drawdown concentration across symbols and accounts">
              <div className="risk-cards">
                <article>
                  <span>Gross Exposure</span>
                  <strong>{formatMoney(riskSnapshot.grossExposure)}</strong>
                </article>
                <article>
                  <span>Drawdown Users</span>
                  <strong>{riskSnapshot.drawdownUsers}</strong>
                </article>
                <article>
                  <span>Largest Exposure</span>
                  <strong>{riskSnapshot.bestSymbol?.symbol ?? "N/A"}</strong>
                </article>
              </div>
              <div className="exposure-list">
                {state.exposureBySymbol.length ? state.exposureBySymbol.map((item) => {
                  const maxExposure = Math.max(...state.exposureBySymbol.map((entry) => entry.exposure), 1);
                  return (
                    <div key={item.symbol} className="exposure-row">
                      <span>{item.symbol}</span>
                      <div className="exposure-bar-wrap">
                        <div className="exposure-bar" style={{ width: `${(item.exposure / maxExposure) * 100}%` }} />
                      </div>
                      <strong>{formatMoney(item.exposure)}</strong>
                    </div>
                  );
                }) : <div className="empty-state">No exposure data available.</div>}
              </div>
            </Panel>

            <Panel title="PnL Snapshot" subtitle="Top and bottom accounts by unrealized performance">
              <div className="leaderboard">
                {mergedUsers.length ? [...mergedUsers]
                  .sort((a, b) => b.totalUnrealizedPnl - a.totalUnrealizedPnl)
                  .slice(0, 10)
                  .map((user) => (
                    <div key={user.id} className="leaderboard-row">
                      <div>
                        <strong>{user.username}</strong>
                        <span>{user.holdingsCount} holdings</span>
                      </div>
                      <div>
                        <strong className={user.totalUnrealizedPnl >= 0 ? "positive-text" : "negative-text"}>
                          {formatSignedMoney(user.totalUnrealizedPnl)}
                        </strong>
                        <span>{formatMoney(user.portfolioValue)}</span>
                      </div>
                    </div>
                  )) : <div className="empty-state">No account performance data available.</div>}
              </div>
            </Panel>
          </section>
          } />

          <Route path="/system" element={
          <section className="dashboard-grid system-grid">
            <Panel title="Detailed Service Health" subtitle="Actuator health, CPU, heap, uptime, DB, disk, and dependency state for every service">
              <div className="system-health-grid">
                {serviceHealthSnapshots.length === 0 ? (
                  <div className="empty-state system-empty-state" role={serviceHealthState === "error" ? "alert" : "status"}>
                    {serviceHealthState === "loading"
                      ? "Loading service health…"
                      : "Service health is unavailable. Check the service connections and refresh."}
                  </div>
                ) : serviceHealthSnapshots.map((service) => {
                  const heapPct = service.heapMaxBytes > 0 ? (service.heapUsedBytes / service.heapMaxBytes) * 100 : 0;
                  return (
                    <article key={service.name} className="service-health-card">
                      <div className="alert-row">
                        <div>
                          <strong>{service.name}</strong>
                          <p className="service-subtext">Port {service.port}</p>
                        </div>
                        <StatusBadge value={service.status} tone={service.statusTone} />
                      </div>

                      <div className="service-health-meta">
                        <div>
                          <span>DB</span>
                          <strong>{service.dbStatus}</strong>
                        </div>
                        <div>
                          <span>Disk</span>
                          <strong>{service.diskStatus ?? "N/A"}</strong>
                        </div>
                        <div>
                          <span>Uptime</span>
                          <strong>{formatDurationSeconds(service.uptimeSeconds)}</strong>
                        </div>
                      </div>

                      <div className="service-health-gauges">
                        <HealthGauge
                          label="Process CPU"
                          value={formatPlainPercent(service.cpuUsagePct)}
                          percent={service.cpuUsagePct}
                          tone={service.cpuUsagePct > 70 ? "critical" : service.cpuUsagePct > 45 ? "warning" : "positive"}
                        />
                        <HealthGauge
                          label="System CPU"
                          value={formatPlainPercent(service.systemCpuUsagePct)}
                          percent={service.systemCpuUsagePct}
                          tone={service.systemCpuUsagePct > 75 ? "critical" : service.systemCpuUsagePct > 50 ? "warning" : "positive"}
                        />
                        <HealthGauge
                          label="Heap Used"
                          value={`${formatBytes(service.heapUsedBytes)} / ${formatBytes(service.heapMaxBytes)}`}
                          percent={heapPct}
                          tone={heapPct > 80 ? "critical" : heapPct > 60 ? "warning" : "positive"}
                        />
                      </div>

                      <details className="system-components">
                        <summary>
                          <span>Dependencies and component details</span>
                          <span className="component-count">{Object.keys(service.components).length} components</span>
                        </summary>
                        {Object.keys(service.components).length ? (
                          <div className="component-grid">
                            {Object.entries(service.components).map(([componentName, component]) => (
                              <div key={componentName} className="component-card">
                                <div className="alert-row">
                                  <strong>{componentName}</strong>
                                  <StatusBadge value={component.status} tone={mapStatusTone(component.status)} />
                                </div>
                                <div className="component-details">
                                  {component.details
                                    ? Object.entries(component.details).slice(0, 3).map(([detailKey, detailValue]) => (
                                        <div key={detailKey}>
                                          <span>{detailKey}</span>
                                          <strong>{String(detailValue)}</strong>
                                        </div>
                                      ))
                                    : <span className="empty-state">No details</span>}
                                </div>
                              </div>
                            ))}
                          </div>
                        ) : <div className="empty-state">No dependency details reported.</div>}
                      </details>
                    </article>
                  );
                })}
              </div>
            </Panel>

            <Panel title="Operations Summary" subtitle="Fast summary cards and active operational signals">
              <div className="service-grid">
                {serviceCards.map((service) => (
                  <article key={service.name} className="service-card">
                    <div className="alert-row">
                      <strong>{service.name}</strong>
                      <StatusBadge value={service.status} tone={service.statusTone} />
                    </div>
                    <span>Port {service.port}</span>
                    <p>{service.note}</p>
                  </article>
                ))}
              </div>
              <div className="alert-list">
                {alerts.length ? alerts.map((alert) => (
                  <article className={`alert-item ${alert.severity}`} key={alert.title}>
                    <div className="alert-row">
                      <strong>{alert.title}</strong>
                      <StatusBadge value={alert.severity.toUpperCase()} tone={alert.severity} />
                    </div>
                    <p>{alert.detail}</p>
                  </article>
                )) : <div className="empty-state">No active operational signals.</div>}
              </div>
            </Panel>
          </section>
          } />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>

      <UserDrawer user={selectedUserView} loading={state.drawerLoading} onClose={() => selectUser(null)} />
    </div>
  );
}
