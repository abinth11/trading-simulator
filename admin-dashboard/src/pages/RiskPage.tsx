import Panel from "../components/Panel";
import { ExposureList, RiskCards } from "../components/RiskSummary";
import type { DashboardData } from "../hooks/useDashboardData";
import { formatMoney, formatSignedMoney } from "../utils/format";

export default function RiskPage({ data }: { data: DashboardData }) {
  const { state, mergedUsers, riskSnapshot } = data;

  return (
    <section className="dashboard-grid">
      <Panel title="Risk Concentration" subtitle="Exposure and drawdown concentration across symbols and accounts">
        <RiskCards risk={riskSnapshot} />
        <ExposureList exposure={state.exposureBySymbol} emptyMessage="No exposure data available." />
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
  );
}
