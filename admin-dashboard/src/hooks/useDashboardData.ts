import { useEffect, useMemo, useState } from "react";
import { dashboardApi, parseSocketPayload } from "../services/api";
import type {
  AdminUser,
  AlertItem,
  DashboardState,
  DerivedOrderBook,
  DistributionSegment,
  KpiMetric,
  LiveConnectionState,
  LiveOrderBookSnapshot,
  LiveOrderFeed,
  MarketSimulationStatus,
  StartMarketSimulationRequest,
  ServiceCardModel,
  ServiceHealthSnapshot
} from "../types";
import { buildDerivedOrderBook, formatCompactNumber, formatInteger, formatMoney, mapStatusTone } from "../utils/format";

const emptyBook: DerivedOrderBook = buildDerivedOrderBook("N/A", 0, 0, 0, 0);

export function useDashboardData() {
  const [orderFeedState, setOrderFeedState] = useState<LiveConnectionState>("connecting");
  const [orderBookState, setOrderBookState] = useState<LiveConnectionState>("idle");
  const [simulationStatus, setSimulationStatus] = useState<MarketSimulationStatus | null>(null);
  const [simulationLoading, setSimulationLoading] = useState(false);
  const [state, setState] = useState<DashboardState>({
    users: [],
    userSummary: null,
    recentOrders: [],
    recentTrades: [],
    orderSummary: null,
    orderStatusMix: [],
    orderTimeline: [],
    symbolActivity: [],
    activeSymbols: [],
    configuredSymbols: [],
    selectedSymbol: "",
    currentBook: emptyBook,
    portfolioSummaries: [],
    exposureBySymbol: [],
    health: {},
    selectedUser: null,
    selectedUserPortfolio: null,
    drawerLoading: false,
    pageLoading: true,
    pageError: "",
    lastUpdated: ""
  });

  useEffect(() => {
    let active = true;

    async function loadDashboard(showLoading = true): Promise<void> {
      if (showLoading) {
        setState((current) => ({ ...current, pageLoading: true, pageError: "" }));
      }

      try {
        const [
          users,
          userSummary,
          recentOrders,
          recentTrades,
          orderSummary,
          orderStatus,
          symbolActivity,
          orderTimeline,
          activeSymbols,
          configuredSymbols,
          portfolioSummaries,
          exposureBySymbol,
          userHealth,
          orderHealth,
          engineHealth,
          portfolioHealth
        ] = await Promise.all([
          dashboardApi.getUsers(),
          dashboardApi.getUserSummary(),
          dashboardApi.getRecentOrders(),
          dashboardApi.getRecentTrades(),
          dashboardApi.getOrderSummary(),
          dashboardApi.getOrderStatusBreakdown(),
          dashboardApi.getSymbolActivity(),
          dashboardApi.getOrderTimeline(),
          dashboardApi.getActiveSymbols(),
          dashboardApi.getConfiguredSymbols(),
          dashboardApi.getPortfolioSummaries(),
          dashboardApi.getExposure(),
          dashboardApi.getHealth("/user-api"),
          dashboardApi.getHealth("/order-api"),
          dashboardApi.getHealth("/engine-api"),
          dashboardApi.getHealth("/portfolio-api")
        ]);

        if (!active) return;

        setState((current) => ({
          ...current,
          users,
          userSummary,
          recentOrders,
          recentTrades,
          orderSummary,
          orderStatusMix: orderStatus.map<DistributionSegment>((item) => ({
            label: item.status,
            value: item.count,
            tone: mapStatusTone(item.status)
          })),
          orderTimeline,
          symbolActivity,
          activeSymbols,
          configuredSymbols,
          selectedSymbol: current.selectedSymbol && activeSymbols.includes(current.selectedSymbol)
            ? current.selectedSymbol
            : (activeSymbols[0] ?? ""),
          portfolioSummaries,
          exposureBySymbol,
          health: {
            "user-service": userHealth.status,
            "order-service": orderHealth.status,
            "matching-engine": engineHealth.status,
            "portfolio-service": portfolioHealth.status
          },
          selectedUser: current.selectedUser
            ? users.find((user) => user.id === current.selectedUser?.id) ?? null
            : current.lastUpdated
              ? null
              : (users[0] ?? null),
          pageLoading: false,
          pageError: "",
          lastUpdated: new Date().toISOString()
        }));
      } catch (error) {
        if (!active) return;
        setState((current) => ({
          ...current,
          pageLoading: false,
          pageError: error instanceof Error ? error.message : "Unknown error"
        }));
      }
    }

    void loadDashboard();

    const intervalId = window.setInterval(() => {
      void loadDashboard(false);
    }, 15000);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  useEffect(() => {
    let active = true;

    async function loadSimulationStatus(): Promise<void> {
      try {
        const status = await dashboardApi.getSimulationStatus();
        if (!active) return;
        setSimulationStatus(status);
      } catch {
        if (!active) return;
        setSimulationStatus(null);
      }
    }

    void loadSimulationStatus();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const socket = dashboardApi.openOrderFeedSocket();
    setOrderFeedState("connecting");

    socket.onopen = () => {
      setOrderFeedState("live");
    };

    socket.onmessage = (event) => {
      const message = parseSocketPayload<LiveOrderFeed>(event);
      if (message.type !== "order-feed") {
        return;
      }

      const payload = message.payload;
      setState((current) => ({
        ...current,
        recentOrders: payload.recentOrders,
        recentTrades: payload.recentTrades,
        orderSummary: payload.summary,
        orderStatusMix: payload.statusBreakdown.map<DistributionSegment>((item) => ({
          label: item.status,
          value: item.count,
          tone: mapStatusTone(item.status)
        })),
        symbolActivity: payload.symbolActivity,
        orderTimeline: payload.timeline,
        lastUpdated: payload.generatedAt
      }));
      setSimulationStatus(payload.simulationStatus);
    };

    socket.onerror = () => {
      setOrderFeedState((current) => (current === "live" ? "reconnecting" : "error"));
    };

    return () => {
      socket.close();
      setOrderFeedState("idle");
    };
  }, []);

  const [serviceHealthSnapshots, setServiceHealthSnapshots] = useState<ServiceHealthSnapshot[]>([]);
  const [serviceHealthState, setServiceHealthState] = useState<"loading" | "ready" | "error">("loading");

  useEffect(() => {
    let active = true;

    async function loadSystemHealth(): Promise<void> {
      try {
        const services = [
          { name: "user-service", port: 8081, prefix: "/user-api", note: "Identity, accounts, and admin user snapshots." },
          { name: "order-service", port: 8082, prefix: "/order-api", note: "Orders, trades, status distribution, and timelines." },
          { name: "matching-engine", port: 8083, prefix: "/engine-api", note: "In-memory order books, spread, and symbol engines." },
          { name: "portfolio-service", port: 8084, prefix: "/portfolio-api", note: "Portfolio valuation, holdings, and exposure." }
        ] as const;

        const snapshots = await Promise.all(
          services.map(async (service) => {
            const [
              health,
              processCpu,
              systemCpu,
              heapUsed,
              heapMax,
              uptime
            ] = await Promise.all([
              dashboardApi.getHealth(service.prefix),
              dashboardApi.getMetric(service.prefix, "process.cpu.usage"),
              dashboardApi.getMetric(service.prefix, "system.cpu.usage"),
              dashboardApi.getMetric(service.prefix, "jvm.memory.used", { area: "heap" }),
              dashboardApi.getMetric(service.prefix, "jvm.memory.max", { area: "heap" }),
              dashboardApi.getMetric(service.prefix, "process.uptime")
            ]);

            const components = health.components ?? {};

            return {
              name: service.name,
              port: service.port,
              status: health.status,
              statusTone: health.status === "UP" ? "positive" : "critical",
              note: service.note,
              cpuUsagePct: getMetricValue(processCpu) * 100,
              systemCpuUsagePct: getMetricValue(systemCpu) * 100,
              heapUsedBytes: getMetricValue(heapUsed),
              heapMaxBytes: getMetricValue(heapMax),
              uptimeSeconds: getMetricValue(uptime),
              dbStatus: components.db?.status ?? "N/A",
              redisStatus: components.redis?.status,
              kafkaStatus: components.kafka?.status,
              diskStatus: components.diskSpace?.status,
              diskFreeBytes: Number(components.diskSpace?.details?.free ?? 0),
              components
            } satisfies ServiceHealthSnapshot;
          })
        );

        if (!active) return;
        setServiceHealthSnapshots(snapshots);
        setServiceHealthState("ready");
      } catch {
        if (!active) return;
        setServiceHealthState("error");
      }
    }

    void loadSystemHealth();
    const intervalId = window.setInterval(() => {
      void loadSystemHealth();
    }, 15000);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  useEffect(() => {
    if (!state.selectedSymbol) {
      setOrderBookState("idle");
      setState((current) => ({ ...current, currentBook: emptyBook }));
      return;
    }

    let active = true;
    const socket = dashboardApi.openOrderBookSocket(state.selectedSymbol);
    setOrderBookState("connecting");

    async function loadBook(): Promise<void> {
      try {
        const book = await dashboardApi.getOrderBook(state.selectedSymbol);
        if (!active) return;
        setState((current) => ({
          ...current,
          currentBook: buildDerivedOrderBook(
            book.symbol,
            Number(book.bestBid ?? 0),
            Number(book.bestAsk ?? 0),
            Number(book.buyDepth ?? 0),
            Number(book.sellDepth ?? 0)
          )
        }));
      } catch {
        if (!active) return;
        setState((current) => ({ ...current, currentBook: emptyBook }));
      }
    }

    void loadBook();

    socket.onopen = () => {
      setOrderBookState("live");
    };

    socket.onmessage = (event) => {
      const message = parseSocketPayload<LiveOrderBookSnapshot>(event);
      if (message.type !== "order-book") {
        return;
      }

      const book = message.payload;
      if (!active) return;
      setState((current) => ({
        ...current,
        currentBook: buildDerivedOrderBook(
          book.symbol,
          Number(book.bestBid ?? 0),
          Number(book.bestAsk ?? 0),
          Number(book.buyDepth ?? 0),
          Number(book.sellDepth ?? 0)
        )
      }));
    };

    socket.onerror = () => {
      if (!active) return;
      setOrderBookState((current) => (current === "live" ? "reconnecting" : "error"));
    };

    return () => {
      active = false;
      socket.close();
    };
  }, [state.selectedSymbol]);

  useEffect(() => {
    if (!state.selectedUser?.id) {
      setState((current) => ({ ...current, selectedUserPortfolio: null }));
      return;
    }

    let active = true;
    setState((current) => ({ ...current, drawerLoading: true }));

    async function loadPortfolio(): Promise<void> {
      try {
        const portfolio = await dashboardApi.getUserPortfolio(state.selectedUser!.id);
        if (!active) return;
        setState((current) => ({
          ...current,
          selectedUserPortfolio: portfolio,
          drawerLoading: false
        }));
      } catch {
        if (!active) return;
        setState((current) => ({
          ...current,
          selectedUserPortfolio: null,
          drawerLoading: false
        }));
      }
    }

    void loadPortfolio();

    return () => {
      active = false;
    };
  }, [state.selectedUser]);

  const metrics = useMemo<KpiMetric[]>(() => {
    if (!state.userSummary || !state.orderSummary) return [];

    const healthyCount = Object.values(state.health).filter((value) => value === "UP").length;
    const degradedCount = 4 - healthyCount;

    return [
      {
        label: "Users",
        value: formatInteger(state.userSummary.totalUsers),
        delta: `${formatInteger(state.userSummary.activeUsers)} active`,
        tone: "neutral"
      },
      {
        label: "Orders",
        value: formatInteger(state.orderSummary.totalOrders),
        delta: `${formatInteger(state.orderSummary.openOrders)} open`,
        tone: "warning"
      },
      {
        label: "Trades Today",
        value: formatInteger(state.orderSummary.tradesToday),
        delta: formatMoney(state.orderSummary.tradedNotional),
        tone: "positive"
      },
      {
        label: "Active Symbols",
        value: formatInteger(state.activeSymbols.length),
        delta: state.activeSymbols.join(" • ") || "No active books",
        tone: state.activeSymbols.length ? "positive" : "warning"
      },
      {
        label: "Fill Rate",
        value: `${Math.round((state.orderSummary.filledOrders / Math.max(state.orderSummary.totalOrders, 1)) * 100)}%`,
        delta: `${formatInteger(state.orderSummary.rejectedOrders)} rejected`,
        tone: state.orderSummary.rejectedOrders ? "warning" : "positive"
      },
      {
        label: "System Status",
        value: healthyCount === 4 ? "Stable" : "Degraded",
        delta: degradedCount ? `${degradedCount} services degraded` : "All core services up",
        tone: degradedCount ? "warning" : "positive"
      }
    ];
  }, [state]);

  const serviceCards = useMemo<ServiceCardModel[]>(
    () =>
      [
        { name: "user-service", port: 8081, status: state.health["user-service"] ?? "DOWN", note: "Identity, accounts, and admin user snapshots." },
        { name: "order-service", port: 8082, status: state.health["order-service"] ?? "DOWN", note: "Orders, trades, status distribution, and timelines." },
        { name: "matching-engine", port: 8083, status: state.health["matching-engine"] ?? "DOWN", note: "In-memory order books, spread, and symbol engines." },
        { name: "portfolio-service", port: 8084, status: state.health["portfolio-service"] ?? "DOWN", note: "Portfolio valuation, holdings, and exposure." }
      ].map((service) => ({
        ...service,
        statusTone: service.status === "UP" ? "positive" : "critical"
      })),
    [state.health]
  );

  const alerts = useMemo<AlertItem[]>(() => {
    const items: AlertItem[] = [];

    if (serviceCards.some((service) => service.status !== "UP")) {
      items.push({
        title: "Core service degradation",
        detail: serviceCards
          .filter((service) => service.status !== "UP")
          .map((service) => service.name)
          .join(", "),
        severity: "critical"
      });
    }

    if (state.orderSummary?.rejectedOrders) {
      items.push({
        title: "Rejected orders present",
        detail: `${formatInteger(state.orderSummary.rejectedOrders)} orders are currently rejected.`,
        severity: "warning"
      });
    }

    if (state.currentBook.bestAsk > 0 && state.currentBook.bestBid > 0) {
      items.push({
        title: "Current spread",
        detail: `${state.currentBook.symbol}: ${formatMoney(state.currentBook.bestAsk - state.currentBook.bestBid)}`,
        severity: "neutral"
      });
    }

    return items;
  }, [serviceCards, state.orderSummary, state.currentBook]);

  const mergedUsers = useMemo(
    () =>
      state.users.map((user) => {
        const summary = state.portfolioSummaries.find((item) => item.userId === user.id);
        return {
          ...user,
          portfolioValue: summary?.totalPortfolioValue ?? user.cashBalance,
          totalUnrealizedPnl: summary?.totalUnrealizedPnl ?? 0,
          holdingsCount: summary?.holdingsCount ?? 0
        };
      }),
    [state.users, state.portfolioSummaries]
  );

  const selectedUserView = useMemo(() => {
    if (!state.selectedUser) return null;
    return {
      ...state.selectedUser,
      orderCount: state.recentOrders.filter((order) => order.userId === state.selectedUser?.id).length,
      portfolioValue: state.selectedUserPortfolio?.totalPortfolioValue ?? state.selectedUser.cashBalance,
      totalUnrealizedPnl: state.selectedUserPortfolio?.totalUnrealizedPnl ?? 0,
      holdings: state.selectedUserPortfolio?.holdings ?? []
    };
  }, [state.selectedUser, state.recentOrders, state.selectedUserPortfolio]);

  const riskSnapshot = useMemo(
    () => ({
      grossExposure: state.exposureBySymbol.reduce((total, item) => total + Number(item.exposure), 0),
      drawdownUsers: state.portfolioSummaries.filter((item) => item.totalUnrealizedPnl < 0).length,
      bestSymbol: state.exposureBySymbol[0]
    }),
    [state.exposureBySymbol, state.portfolioSummaries]
  );

  function selectSymbol(symbol: string): void {
    setState((current) => ({ ...current, selectedSymbol: symbol }));
  }

  function selectUser(user: AdminUser | null): void {
    setState((current) => ({ ...current, selectedUser: user }));
  }

  function refreshDashboard(): void {
    setState((current) => ({ ...current, pageLoading: true, pageError: "" }));
    void Promise.all([
      dashboardApi.getUsers(),
      dashboardApi.getUserSummary(),
      dashboardApi.getRecentOrders(),
      dashboardApi.getRecentTrades(),
      dashboardApi.getOrderSummary(),
      dashboardApi.getOrderStatusBreakdown(),
      dashboardApi.getSymbolActivity(),
      dashboardApi.getOrderTimeline(),
      dashboardApi.getActiveSymbols(),
      dashboardApi.getConfiguredSymbols(),
      dashboardApi.getPortfolioSummaries(),
      dashboardApi.getExposure(),
      dashboardApi.getHealth("/user-api"),
      dashboardApi.getHealth("/order-api"),
      dashboardApi.getHealth("/engine-api"),
      dashboardApi.getHealth("/portfolio-api")
    ]).then(([
      users,
      userSummary,
      recentOrders,
      recentTrades,
      orderSummary,
      orderStatus,
      symbolActivity,
      orderTimeline,
      activeSymbols,
      configuredSymbols,
      portfolioSummaries,
      exposureBySymbol,
      userHealth,
      orderHealth,
      engineHealth,
      portfolioHealth
    ]) => {
      setState((current) => ({
        ...current,
        users,
        userSummary,
        recentOrders,
        recentTrades,
        orderSummary,
        orderStatusMix: orderStatus.map<DistributionSegment>((item) => ({
          label: item.status,
          value: item.count,
          tone: mapStatusTone(item.status)
        })),
        orderTimeline,
        symbolActivity,
        activeSymbols,
        configuredSymbols,
        selectedSymbol: current.selectedSymbol && activeSymbols.includes(current.selectedSymbol)
          ? current.selectedSymbol
          : (activeSymbols[0] ?? ""),
        portfolioSummaries,
        exposureBySymbol,
        health: {
          "user-service": userHealth.status,
          "order-service": orderHealth.status,
          "matching-engine": engineHealth.status,
          "portfolio-service": portfolioHealth.status
        },
        selectedUser: current.selectedUser
          ? users.find((user) => user.id === current.selectedUser?.id) ?? null
          : null,
        pageLoading: false,
        pageError: "",
        lastUpdated: new Date().toISOString()
      }));
    }).catch((error: unknown) => {
      setState((current) => ({
        ...current,
        pageLoading: false,
        pageError: error instanceof Error ? error.message : "Unknown error"
      }));
    });
  }

  async function startSimulation(request: StartMarketSimulationRequest): Promise<void> {
    setSimulationLoading(true);
    try {
      const status = await dashboardApi.startSimulation(request);
      setSimulationStatus(status);
    } finally {
      setSimulationLoading(false);
    }
  }

  async function stopSimulation(): Promise<void> {
    setSimulationLoading(true);
    try {
      const status = await dashboardApi.stopSimulation();
      setSimulationStatus(status);
    } finally {
      setSimulationLoading(false);
    }
  }

  return {
    state,
    metrics,
    alerts,
    serviceCards,
    mergedUsers,
    selectedUserView,
    riskSnapshot,
    selectSymbol,
    selectUser,
    refreshDashboard,
    simulationStatus,
    simulationLoading,
    startSimulation,
    stopSimulation,
    serviceHealthSnapshots,
    serviceHealthState,
    liveState: {
      orderFeed: orderFeedState,
      orderBook: orderBookState
    },
    marketPulse: {
      spread: state.currentBook.bestAsk - state.currentBook.bestBid,
      bestBid: state.currentBook.bestBid,
      bestAsk: state.currentBook.bestAsk,
      buyDepth: state.currentBook.buyDepth,
      sellDepth: state.currentBook.sellDepth,
      topVolumeSymbol: state.symbolActivity[0]?.symbol ?? "N/A",
      topVolumeValue: state.symbolActivity[0]?.tradedNotional ?? 0,
      activeFlow: state.orderSummary?.openOrders ?? 0
    },
    headlineNumbers: {
      totalExposure: formatMoney(riskSnapshot.grossExposure),
      drawdownUsers: formatInteger(riskSnapshot.drawdownUsers),
      activeOrderFlow: formatCompactNumber(state.orderSummary?.openOrders ?? 0)
    }
  };
}

function getMetricValue(metric: { measurements: Array<{ value: number }> }): number {
  return metric.measurements[0]?.value ?? 0;
}
