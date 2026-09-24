interface HealthGaugeProps {
  label: string;
  value: string;
  percent: number;
  tone?: "positive" | "warning" | "critical" | "neutral";
}

export default function HealthGauge({ label, value, percent, tone = "neutral" }: HealthGaugeProps) {
  const bounded = Math.max(0, Math.min(percent, 100));

  return (
    <div className="health-gauge">
      <div className="health-gauge-header">
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <div className="health-gauge-track">
        <div className={`health-gauge-fill ${tone}`} style={{ width: `${bounded}%` }} />
      </div>
    </div>
  );
}
