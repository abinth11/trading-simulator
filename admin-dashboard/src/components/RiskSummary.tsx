import type { DashboardData } from "../hooks/useDashboardData";
import type { SymbolExposure } from "../types";
import { formatMoney } from "../utils/format";

export function RiskCards({ risk }: { risk: DashboardData["riskSnapshot"] }) {
  return (
    <div className="risk-cards">
      <article>
        <span>Gross Exposure</span>
        <strong>{formatMoney(risk.grossExposure)}</strong>
      </article>
      <article>
        <span>Drawdown Users</span>
        <strong>{risk.drawdownUsers}</strong>
      </article>
      <article>
        <span>Largest Exposure</span>
        <strong>{risk.bestSymbol?.symbol ?? "N/A"}</strong>
      </article>
    </div>
  );
}

interface ExposureListProps {
  exposure: SymbolExposure[];
  limit?: number;
  // Omit to render nothing when there is no exposure
  emptyMessage?: string;
}

export function ExposureList({ exposure, limit, emptyMessage }: ExposureListProps) {
  const maxExposure = Math.max(...exposure.map((entry) => entry.exposure), 1);
  const rows = limit === undefined ? exposure : exposure.slice(0, limit);

  return (
    <div className="exposure-list">
      {rows.length ? rows.map((item) => (
        <div key={item.symbol} className="exposure-row">
          <span>{item.symbol}</span>
          <div className="exposure-bar-wrap">
            <div className="exposure-bar" style={{ width: `${(item.exposure / maxExposure) * 100}%` }} />
          </div>
          <strong>{formatMoney(item.exposure)}</strong>
        </div>
      )) : emptyMessage ? <div className="empty-state">{emptyMessage}</div> : null}
    </div>
  );
}
