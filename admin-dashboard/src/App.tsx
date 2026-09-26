import { useEffect, useMemo, useState } from "react";
import { Navigate, NavLink, Route, Routes, useLocation } from "react-router-dom";
import Panel from "./components/Panel";
import UserDrawer from "./components/UserDrawer";
import { useDashboardData } from "./hooks/useDashboardData";
import { useSimulationSettings } from "./hooks/useSimulationSettings";
import OrderBookPage from "./pages/OrderBookPage";
import OverviewPage from "./pages/OverviewPage";
import RiskPage from "./pages/RiskPage";
import SymbolsPage from "./pages/SymbolsPage";
import SystemPage from "./pages/SystemPage";
import TradingPage from "./pages/TradingPage";
import UsersPage from "./pages/UsersPage";
import { logout, useAuth } from "./services/auth";
import { navigationItems, serviceEndpoints, type NavigationTab } from "./types";
import { formatTime } from "./utils/format";

const pageDescriptions: Record<NavigationTab, string> = {
  overview: "A live view of market activity, execution quality, platform health, and exposure.",
  trading: "Search recent orders, review execution activity, and control the market simulator.",
  orderbook: "Inspect live bid and ask depth across active matching-engine books.",
  symbols: "Manage configured symbols and choose which markets the simulator uses.",
  users: "Review account status, balances, portfolio value, and holdings.",
  risk: "Monitor exposure concentration and unrealized account performance.",
  system: "Review service health, resource usage, and dependency status."
};

export default function App() {
  const [theme, setTheme] = useState<"dark" | "light">(() =>
    document.documentElement.dataset.theme === "light" ? "light" : "dark"
  );
  const location = useLocation();
  const { session } = useAuth();
  const data = useDashboardData();
  const { state, liveState, selectedUserView, selectUser, refreshDashboard, simulationStatus } = data;
  const simulationSettings = useSimulationSettings(state.configuredSymbols, simulationStatus);

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

  const tabTitle = navigationItems.find((item) => item.id === activeTab)?.label ?? "Overview";
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

  if (state.pageLoading && !state.lastUpdated) {
    return <div className="app-loading">Booting trading workstation...</div>;
  }

  if (state.pageError && !state.lastUpdated) {
    return <div className="app-loading">Dashboard load failed: {state.pageError}</div>;
  }

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
          <div className="brand-mark" aria-hidden="true">TS</div>
          <div>
            <h1>Trading Simulator</h1>
            <p>Operator Workstation</p>
          </div>
        </div>

        <div className="nav-group-label">Navigation</div>
        <nav className="nav-list" aria-label="Main navigation">
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
              aria-label={`Use ${theme === "dark" ? "light" : "dark"} theme`}
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
            <button
              className="ghost-button"
              type="button"
              title={session ? `Signed in as ${session.user.email}` : undefined}
              onClick={() => logout()}
            >
              Sign out
            </button>
          </div>
        </header>

        {state.pageError && state.lastUpdated ? (
          <div className="dashboard-notice error-notice" role="alert">
            Refresh failed: {state.pageError}. Showing the last successful update from {formatTime(state.lastUpdated)}.
          </div>
        ) : null}

        <Routes>
          <Route path="/" element={<OverviewPage data={data} />} />
          <Route path="/trading" element={<TradingPage data={data} settings={simulationSettings} />} />
          <Route path="/orderbook" element={<OrderBookPage data={data} />} />
          <Route path="/symbols" element={<SymbolsPage data={data} settings={simulationSettings} />} />
          <Route path="/users" element={<UsersPage data={data} />} />
          <Route path="/risk" element={<RiskPage data={data} />} />
          <Route path="/system" element={<SystemPage data={data} />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>

      <UserDrawer user={selectedUserView} loading={state.drawerLoading} onClose={() => selectUser(null)} />
    </div>
  );
}
