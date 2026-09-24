import type { Tone } from "../types";

interface StatusBadgeProps {
  value: string;
  tone?: Tone;
}

export default function StatusBadge({ value, tone = "neutral" }: StatusBadgeProps) {
  const normalized = tone || "neutral";
  return <span className={`status-badge ${normalized}`}>{value}</span>;
}
