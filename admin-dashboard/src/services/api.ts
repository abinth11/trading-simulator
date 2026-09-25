import type {
  AdminOrder,
  AdminOrderSummary,
  AdminTrade,
  AdminUser,
  AdminUserSummary,
  HealthResponse,
  MetricResponse,
  MarketSimulationStatus,
  LiveOrderBookSnapshot,
  LiveOrderFeed,
  OrderBookSnapshot,
  PortfolioResponse,
  SocketEnvelope,
  StartMarketSimulationRequest,
  StatusBreakdownItem,
  SymbolActivityItem,
  SymbolExposure,
  TimelinePoint,
  UserPortfolioSummary
} from "../types";
import { authorizedFetch, getAccessToken } from "./auth";

const JSON_HEADERS: HeadersInit = {
  "Content-Type": "application/json"
};

async function fetchJson<T>(path: string): Promise<T> {
  const response = await authorizedFetch(path, { headers: JSON_HEADERS });
  if (!response.ok) {
    throw new Error(`Request failed for ${path}: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

async function sendJson<TResponse, TBody>(path: string, method: "POST", body?: TBody): Promise<TResponse> {
  const response = await authorizedFetch(path, {
    method,
    headers: JSON_HEADERS,
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  if (!response.ok) {
    throw new Error(`Request failed for ${path}: ${response.status}`);
  }

  return response.json() as Promise<TResponse>;
}

export const dashboardApi = {
  getUsers: (): Promise<AdminUser[]> => fetchJson("/user-api/api/v1/admin/users"),
  getUserSummary: (): Promise<AdminUserSummary> => fetchJson("/user-api/api/v1/admin/users/summary"),
  getRecentOrders: (): Promise<AdminOrder[]> => fetchJson("/order-api/api/v1/admin/orders/recent?limit=20"),
  getRecentTrades: (): Promise<AdminTrade[]> => fetchJson("/order-api/api/v1/admin/orders/trades/recent?limit=12"),
  getOrderSummary: (): Promise<AdminOrderSummary> => fetchJson("/order-api/api/v1/admin/orders/summary"),
  getOrderStatusBreakdown: (): Promise<StatusBreakdownItem[]> => fetchJson("/order-api/api/v1/admin/orders/status-breakdown"),
  getSymbolActivity: (): Promise<SymbolActivityItem[]> => fetchJson("/order-api/api/v1/admin/orders/symbol-activity"),
  getOrderTimeline: (): Promise<TimelinePoint[]> => fetchJson("/order-api/api/v1/admin/orders/timeline"),
  getActiveSymbols: (): Promise<string[]> => fetchJson("/engine-api/api/v1/engine/symbols"),
  getConfiguredSymbols: (): Promise<string[]> => fetchJson("/order-api/api/v1/admin/simulation/symbols"),
  getOrderBook: (symbol: string): Promise<OrderBookSnapshot> => fetchJson(`/engine-api/api/v1/engine/orderbook/${symbol}`),
  getPortfolioSummaries: (): Promise<UserPortfolioSummary[]> => fetchJson("/portfolio-api/api/v1/admin/portfolio/users"),
  getUserPortfolio: (userId: string): Promise<PortfolioResponse> => fetchJson(`/portfolio-api/api/v1/admin/portfolio/users/${userId}`),
  getExposure: (): Promise<SymbolExposure[]> => fetchJson("/portfolio-api/api/v1/admin/portfolio/exposure"),
  getSimulationStatus: (): Promise<MarketSimulationStatus> => fetchJson("/order-api/api/v1/admin/simulation"),
  startSimulation: (request: StartMarketSimulationRequest): Promise<MarketSimulationStatus> =>
    sendJson("/order-api/api/v1/admin/simulation/start", "POST", request),
  stopSimulation: (): Promise<MarketSimulationStatus> =>
    sendJson("/order-api/api/v1/admin/simulation/stop", "POST"),
  getHealth: (servicePrefix: string): Promise<HealthResponse> => fetchJson(`${servicePrefix}/actuator/health`),
  getMetric: (servicePrefix: string, metricName: string, params?: Record<string, string>): Promise<MetricResponse> => {
    const search = new URLSearchParams(params ?? {}).toString();
    const suffix = search ? `?${search}` : "";
    return fetchJson(`${servicePrefix}/actuator/metrics/${metricName}${suffix}`);
  },
  // Browsers can't send headers on a WebSocket, so the admin feed takes the token as a query parameter
  openOrderFeedSocket: (orderLimit = 20, tradeLimit = 12): WebSocket =>
    createSocket(
      `/order-api/ws/admin/orders?orderLimit=${orderLimit}&tradeLimit=${tradeLimit}` +
        `&token=${encodeURIComponent(getAccessToken() ?? "")}`
    ),
  openOrderBookSocket: (symbol: string): WebSocket =>
    createSocket(`/engine-api/ws/engine/orderbook?symbol=${encodeURIComponent(symbol)}`)
};

function createSocket(path: string): WebSocket {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return new WebSocket(`${protocol}//${window.location.host}${path}`);
}

export function parseSocketPayload<T>(event: MessageEvent<string>): SocketEnvelope<T> {
  return JSON.parse(event.data) as SocketEnvelope<T>;
}
