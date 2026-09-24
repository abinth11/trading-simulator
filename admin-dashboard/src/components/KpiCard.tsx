import type { KpiMetric } from "../types";
import StatusBadge from "./StatusBadge";

interface KpiCardProps {
  metric: KpiMetric;
}

export default function KpiCard({ metric }: KpiCardProps) {
  return (
    <article className="kpi-card">
      <div className="kpi-header">
        <span>{metric.label}</span>
        <StatusBadge value={metric.tone.toUpperCase()} tone={metric.tone} />
      </div>
      <div className="kpi-value">{metric.value}</div>
      <div className="kpi-delta">{metric.delta}</div>
    </article>
  );
}
