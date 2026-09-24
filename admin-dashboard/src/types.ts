import type { ReactNode } from "react";

export type Tone = "positive" | "warning" | "critical" | "neutral";

export interface ServiceEndpointMap {
  userService: string;
  orderService: string;
  matchingEngine: string;
  portfolioService: string;
}

export interface HealthResponse {
  status: "UP" | "DOWN" | string;
  components?: Record<string, HealthComponent>;
}

export interface HealthComponent {
  status: string;
  details?: Record<string, string | number | boolean | null>;
}

export interface MetricResponse {
  name: string;
  description?: string;
  baseUnit?: string;
  measurements: Array<{
    statistic: string;
    value: number;
  }>;
}

export interface AdminUser {
  id: string;
  email: string;
  username: string;
  cashBalance: number;
  role: "USER" | "ADMIN" | "BOT" | string;
  isActive: boolean;
  createdAt: string;
}

export interface AdminUserSummary {
  totalUsers: number;
  activeUsers: number;
  inactiveUsers: number;
  adminUsers: number;
  botUsers: number;
}

export interface AdminOrder {
  id: string;
  userId: string;
  username: string;
  symbol: string;
  side: "BUY" | "SELL" | string;
  orderType: "LIMIT" | "MARKET" | string;
  price: number | null;
  quantity: number;
  filledQuantity: number;
  remainingQuantity: number;
  status: "PENDING" | "PARTIAL" | "FILLED" | "CANCELLED" | "REJECTED" | string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminTrade {
  id: string;
  symbol: string;
  price: number;
  quantity: number;
  buyer: string;
  seller: string;
  executedAt: string;
}

export interface LiveOrderFeed {
  recentOrders: AdminOrder[];
  recentTrades: AdminTrade[];
  summary: AdminOrderSummary;
  statusBreakdown: StatusBreakdownItem[];
  symbolActivity: SymbolActivityItem[];
  timeline: TimelinePoint[];
  simulationStatus: MarketSimulationStatus;
  generatedAt: string;
}

export interface AdminOrderSummary {
  totalOrders: number;
  openOrders: number;
  filledOrders: number;
  cancelledOrders: number;
  rejectedOrders: number;
  tradesToday: number;
  tradedNotional: number;
}

export interface StatusBreakdownItem {
  status: string;
  count: number;
}

export interface SymbolActivityItem {
  symbol: string;
  orders: number;
  filledOrders: number;
  tradedNotional: number;
}

export interface TimelinePoint {
  bucket: string;
  orders: number;
  fills: number;
  volume: number;
}

export interface OrderBookSnapshot {
  symbol: string;
  bestBid: number;
  bestAsk: number;
  buyDepth: number;
  sellDepth: number;
}

export interface LiveOrderBookSnapshot extends OrderBookSnapshot {
  generatedAt: string;
}

export interface LadderLevel {
  price: number;
  quantity: number;
}

export interface DerivedOrderBook extends OrderBookSnapshot {
  buyLadder: LadderLevel[];
  sellLadder: LadderLevel[];
}

export interface Holding {
  symbol: string;
  quantity: number;
  avgBuyPrice: number;
  lastPrice: number;
  marketValue: number;
  unrealizedPnl: number;
  unrealizedPnlPct: number;
}

export interface PortfolioResponse {
  userId: string;
  cashBalance: number;
  totalMarketValue: number;
  totalPortfolioValue: number;
  totalUnrealizedPnl: number;
  holdings: Holding[];
}

export interface UserPortfolioSummary {
  userId: string;
  username: string;
  cashBalance: number;
  totalMarketValue: number;
  totalPortfolioValue: number;
  totalUnrealizedPnl: number;
  holdingsCount: number;
}

export interface SymbolExposure {
  symbol: string;
  exposure: number;
}

export interface KpiMetric {
  label: string;
  value: string;
  delta: string;
  tone: Tone;
}

export interface AlertItem {
  title: string;
  detail: string;
  severity: Tone;
}

export interface ServiceCardModel {
  name: string;
  port: number;
  status: string;
  note: string;
  statusTone: Tone;
}

export interface ServiceHealthSnapshot {
  name: string;
  port: number;
  status: string;
  statusTone: Tone;
  note: string;
  cpuUsagePct: number;
  systemCpuUsagePct: number;
  heapUsedBytes: number;
  heapMaxBytes: number;
  uptimeSeconds: number;
  dbStatus: string;
  redisStatus?: string;
  kafkaStatus?: string;
  diskStatus?: string;
  diskFreeBytes?: number;
  components: Record<string, HealthComponent>;
}

export interface MarketSimulationStatus {
  running: boolean;
  intervalMs: number;
  ordersPerTick: number;
  symbols: string[];
  startedAt: string | null;
  stoppedAt: string | null;
  lastTickAt: string | null;
  ticksExecuted: number;
  generatedOrders: number;
  successfulOrders: number;
  failedOrders: number;
  lastError: string | null;
}

export interface StartMarketSimulationRequest {
  intervalMs: number;
  ordersPerTick: number;
  symbols: string[];
}

export interface TableColumn<TRow> {
  key: keyof TRow | string;
  label: string;
  align?: "left" | "right";
  className?: string;
  render?: (row: TRow) => ReactNode;
  sortable?: boolean;
  sortValue?: (row: TRow) => string | number;
}

export interface DistributionSegment {
  label: string;
  value: number;
  tone: Tone;
}

export interface SelectionPill {
  label: string;
  active?: boolean;
}

export interface DashboardState {
  users: AdminUser[];
  userSummary: AdminUserSummary | null;
  recentOrders: AdminOrder[];
  recentTrades: AdminTrade[];
  orderSummary: AdminOrderSummary | null;
  orderStatusMix: DistributionSegment[];
  orderTimeline: TimelinePoint[];
  symbolActivity: SymbolActivityItem[];
  activeSymbols: string[];
  configuredSymbols: string[];
  selectedSymbol: string;
  currentBook: DerivedOrderBook;
  portfolioSummaries: UserPortfolioSummary[];
  exposureBySymbol: SymbolExposure[];
  health: Record<string, string>;
  selectedUser: AdminUser | null;
  selectedUserPortfolio: PortfolioResponse | null;
  drawerLoading: boolean;
  pageLoading: boolean;
  pageError: string;
  lastUpdated: string;
}

export type LiveConnectionState = "idle" | "connecting" | "live" | "reconnecting" | "error";

export interface SocketEnvelope<TPayload> {
  type: string;
  payload: TPayload;
}

export type NavigationTab = "overview" | "trading" | "orderbook" | "symbols" | "users" | "risk" | "system";

export interface NavigationItem {
  id: NavigationTab;
  label: string;
  path: string;
}

export const serviceEndpoints: ServiceEndpointMap = {
  userService: "/user-api",
  orderService: "/order-api",
  matchingEngine: "/engine-api",
  portfolioService: "/portfolio-api"
};

export const navigationItems: NavigationItem[] = [
  { id: "overview", label: "Overview", path: "/" },
  { id: "trading", label: "Trading", path: "/trading" },
  { id: "orderbook", label: "Order Book", path: "/orderbook" },
  { id: "symbols", label: "Symbols", path: "/symbols" },
  { id: "users", label: "Users", path: "/users" },
  { id: "risk", label: "Risk", path: "/risk" },
  { id: "system", label: "System", path: "/system" }
];
