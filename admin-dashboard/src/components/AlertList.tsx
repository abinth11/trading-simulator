import type { DashboardData } from "../hooks/useDashboardData";
import StatusBadge from "./StatusBadge";

interface AlertListProps {
  alerts: DashboardData["alerts"];
  emptyMessage: string;
}

export default function AlertList({ alerts, emptyMessage }: AlertListProps) {
  return (
    <div className="alert-list">
      {alerts.length ? alerts.map((alert) => (
        <article className={`alert-item ${alert.severity}`} key={alert.title}>
          <div className="alert-row">
            <strong>{alert.title}</strong>
            <StatusBadge value={alert.severity.toUpperCase()} tone={alert.severity} />
          </div>
          <p>{alert.detail}</p>
        </article>
      )) : <div className="empty-state">{emptyMessage}</div>}
    </div>
  );
}
