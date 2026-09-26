import type { DerivedOrderBook, Tone } from "../types";

export function formatCompactNumber(value: number): string {
  return new Intl.NumberFormat("en-IN", {
    notation: "compact",
    maximumFractionDigits: 1
  }).format(value);
}

export function formatInteger(value: number): string {
  return new Intl.NumberFormat("en-IN", {
    maximumFractionDigits: 0
  }).format(value);
}

export function formatMoney(value: number): string {
  return `₹${new Intl.NumberFormat("en-IN", {
    maximumFractionDigits: 2
  }).format(value)}`;
}

export function formatSignedMoney(value: number): string {
  const sign = value > 0 ? "+" : "";
  return `${sign}${formatMoney(value)}`;
}

export function formatPercent(value: number): string {
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(2)}%`;
}

export function formatPlainPercent(value: number): string {
  return `${value.toFixed(2)}%`;
}

export function formatBytes(value: number): string {
  if (!Number.isFinite(value) || value <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let size = value;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size.toFixed(size >= 10 ? 0 : 1)} ${units[unitIndex]}`;
}

export function formatDurationSeconds(value: number): string {
  if (!Number.isFinite(value) || value <= 0) return "0s";
  const hours = Math.floor(value / 3600);
  const minutes = Math.floor((value % 3600) / 60);
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

export function formatTime(value: string): string {
  return new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  });
}

export function formatDate(value: string): string {
  return new Date(value).toLocaleDateString();
}

export function mapStatusTone(status: string): Tone {
  const value = status.toUpperCase();
  if (value === "FILLED" || value === "UP" || value === "ACTIVE") return "positive";
  if (value === "PARTIAL" || value === "PENDING" || value === "CANCELLING" || value === "MARKET" || value === "BOT") return "warning";
  if (value === "REJECTED" || value === "CANCELLED" || value === "SELL" || value === "DOWN" || value === "INACTIVE") return "critical";
  return "neutral";
}

export function buildDerivedOrderBook(
  symbol: string,
  bestBid: number,
  bestAsk: number,
  buyDepth: number,
  sellDepth: number
): DerivedOrderBook {
  return {
    symbol,
    bestBid,
    bestAsk,
    buyDepth,
    sellDepth,
    buyLadder: Array.from({ length: 6 }, (_, index) => ({
      price: Math.max(bestBid - index * 0.25, 0),
      quantity: Math.max(Math.round(buyDepth * 18 - index * 110), 0)
    })),
    sellLadder: Array.from({ length: 6 }, (_, index) => ({
      price: Math.max(bestAsk + index * 0.25, 0),
      quantity: Math.max(Math.round(sellDepth * 18 - index * 110), 0)
    }))
  };
}
