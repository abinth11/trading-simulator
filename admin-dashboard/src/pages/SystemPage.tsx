import { useMemo } from "react";
import AlertList from "../components/AlertList";
import HealthGauge from "../components/HealthGauge";
import Panel from "../components/Panel";
import ServiceCardGrid from "../components/ServiceCardGrid";
import StatusBadge from "../components/StatusBadge";
import type { DashboardData } from "../hooks/useDashboardData";
import { formatBytes, formatDurationSeconds, formatPlainPercent, mapStatusTone } from "../utils/format";

export default function SystemPage({ data }: { data: DashboardData }) {
  const { alerts, serviceCards, serviceHealthSnapshots, serviceHealthState } = data;

  const summary = useMemo(() => {
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

    return { healthy, degraded, highestCpu, highestHeap };
  }, [serviceHealthSnapshots]);

  const hasSnapshots = serviceHealthSnapshots.length > 0;

  return (
    <>
      {serviceHealthState === "error" && hasSnapshots ? (
        <div className="dashboard-notice warning-notice" role="status">
          System health could not be refreshed. The service details below are from the last successful check.
        </div>
      ) : null}

      <section className="system-topbar">
        <article>
          <span>Healthy Services</span>
          <strong>{hasSnapshots ? summary.healthy : "—"}</strong>
        </article>
        <article>
          <span>Degraded Services</span>
          <strong>{hasSnapshots ? summary.degraded : "—"}</strong>
        </article>
        <article>
          <span>Peak System CPU</span>
          <strong>{hasSnapshots ? formatPlainPercent(summary.highestCpu) : "—"}</strong>
        </article>
        <article>
          <span>Peak Heap Usage</span>
          <strong>{hasSnapshots ? formatPlainPercent(summary.highestHeap) : "—"}</strong>
        </article>
      </section>

      <section className="dashboard-grid system-grid">
        <Panel title="Detailed Service Health" subtitle="Actuator health, CPU, heap, uptime, DB, disk, and dependency state for every service">
          <div className="system-health-grid">
            {!hasSnapshots ? (
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
          <ServiceCardGrid services={serviceCards} />
          <AlertList alerts={alerts} emptyMessage="No active operational signals." />
        </Panel>
      </section>
    </>
  );
}
