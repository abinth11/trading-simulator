import type { DistributionSegment } from "../types";

interface DistributionBarProps {
  segments: DistributionSegment[];
}

export default function DistributionBar({ segments }: DistributionBarProps) {
  const total = Math.max(segments.reduce((sum, segment) => sum + segment.value, 0), 1);

  return (
    <div className="distribution">
      <div className="distribution-bar">
        {segments.map((segment) => (
          <div
            key={segment.label}
            className={`distribution-segment ${segment.tone}`}
            style={{ width: `${(segment.value / total) * 100}%` }}
            title={`${segment.label}: ${segment.value}`}
          />
        ))}
      </div>
      <div className="distribution-list">
        {segments.map((segment) => (
          <div key={segment.label} className="distribution-row">
            <div className="distribution-label">
              <span className={`distribution-dot ${segment.tone}`} />
              <strong>{segment.label}</strong>
            </div>
            <span>{segment.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
