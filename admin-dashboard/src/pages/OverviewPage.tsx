import AlertList from "../components/AlertList";
import KpiCard from "../components/KpiCard";
import MarketActivityChart from "../components/MarketActivityChart";
import Panel from "../components/Panel";
import { ExposureList, RiskCards } from "../components/RiskSummary";
import ServiceCardGrid from "../components/ServiceCardGrid";
import type { DashboardData } from "../hooks/useDashboardData";
import { formatCompactNumber, formatMoney, formatSignedMoney } from "../utils/format";

export default function OverviewPage({ data }: { data: DashboardData }) {
  const { state, metrics, alerts, serviceCards, mergedUsers, riskSnapshot, marketPulse, headlineNumbers } = data;

  return (
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

      <section className="dashboard-grid hero-grid">
        <Panel title="Market Activity" subtitle="Intraday orders, fills, and notional volume stitched into a compact trading chart">
          <MarketActivityChart data={state.orderTimeline} />
        </Panel>

        <Panel title="Control Signals" subtitle="Operational alerts and quick risk pulse">
          <AlertList alerts={alerts} emptyMessage="No active control signals." />
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
          <ServiceCardGrid services={serviceCards} />
        </Panel>

        <Panel title="Capital At Risk" subtitle="High-level exposure and drawdown read without leaving the overview">
          <RiskCards risk={riskSnapshot} />
          <ExposureList exposure={state.exposureBySymbol} limit={5} />
        </Panel>
      </section>

      <section className="dashboard-grid">
        <Panel title="Market Leaders" subtitle="Cross-symbol snapshot of the most active names on the platform">
          <div className="leaderboard">
            {state.symbolActivity.length ? state.symbolActivity.slice(0, 6).map((item) => (
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
  );
}
