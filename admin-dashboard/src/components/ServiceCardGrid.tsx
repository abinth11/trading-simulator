import type { DashboardData } from "../hooks/useDashboardData";
import StatusBadge from "./StatusBadge";

export default function ServiceCardGrid({ services }: { services: DashboardData["serviceCards"] }) {
  return (
    <div className="service-grid">
      {services.map((service) => (
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
  );
}
