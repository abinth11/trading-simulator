import type { TimelinePoint } from "../types";
import { formatCompactNumber } from "../utils/format";

interface MarketActivityChartProps {
  data: TimelinePoint[];
}

export default function MarketActivityChart({ data }: MarketActivityChartProps) {
  const width = 720;
  const height = 220;
  const padding = 22;
  const maxValue = Math.max(
    ...data.flatMap((point) => [point.orders, point.fills, point.volume]),
    1
  );

  const createPath = (selector: (point: TimelinePoint) => number): string => {
    if (!data.length) return "";
    return data
      .map((point, index) => {
        const x = padding + (index * (width - padding * 2)) / Math.max(data.length - 1, 1);
        const y = height - padding - (selector(point) / maxValue) * (height - padding * 2);
        return `${index === 0 ? "M" : "L"} ${x} ${y}`;
      })
      .join(" ");
  };

  const last = data[data.length - 1];

  return (
    <div className="activity-chart">
      <svg viewBox={`0 0 ${width} ${height}`} className="chart-svg" role="img" aria-label="Trading activity chart">
        <defs>
          <linearGradient id="ordersGradient" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="rgba(76, 160, 255, 0.9)" />
            <stop offset="100%" stopColor="rgba(76, 160, 255, 0.16)" />
          </linearGradient>
        </defs>

        {[0.25, 0.5, 0.75].map((ratio) => (
          <line
            key={ratio}
            x1={padding}
            x2={width - padding}
            y1={height - padding - ratio * (height - padding * 2)}
            y2={height - padding - ratio * (height - padding * 2)}
            className="chart-gridline"
          />
        ))}

        <path d={createPath((point) => point.orders)} className="chart-line chart-line-orders" />
        <path d={createPath((point) => point.fills)} className="chart-line chart-line-fills" />
        <path d={createPath((point) => point.volume)} className="chart-line chart-line-volume" />
      </svg>

      <div className="chart-legend">
        <span><i className="legend orders" />Orders</span>
        <span><i className="legend fills" />Fills</span>
        <span><i className="legend volume" />Volume</span>
      </div>

      {last ? (
        <div className="chart-footer">
          <div>
            <span>Latest Bucket</span>
            <strong>{last.bucket}</strong>
          </div>
          <div>
            <span>Orders</span>
            <strong>{formatCompactNumber(last.orders)}</strong>
          </div>
          <div>
            <span>Volume</span>
            <strong>{formatCompactNumber(last.volume)}</strong>
          </div>
        </div>
      ) : (
        <div className="empty-state">No intraday data available.</div>
      )}
    </div>
  );
}
