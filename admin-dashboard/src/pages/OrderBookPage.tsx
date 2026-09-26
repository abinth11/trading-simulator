import OrderBookDepth from "../components/OrderBookDepth";
import Panel from "../components/Panel";
import type { DashboardData } from "../hooks/useDashboardData";
import { formatCompactNumber, formatMoney } from "../utils/format";

export default function OrderBookPage({ data }: { data: DashboardData }) {
  const { state, liveState, selectSymbol } = data;
  const book = state.currentBook;

  return (
    <section className="dashboard-grid">
      <Panel title="Depth Monitor" subtitle="Live order-book inspection for active in-memory symbols">
        <div className="symbol-tabs">
          {state.activeSymbols.map((symbol) => (
            <button
              key={symbol}
              className={`symbol-tab ${state.selectedSymbol === symbol ? "active" : ""}`}
              onClick={() => selectSymbol(symbol)}
              type="button"
            >
              {symbol}
            </button>
          ))}
        </div>
        <div className="orderbook-header">
          <article>
            <span>Symbol</span>
            <strong>{book.symbol}</strong>
          </article>
          <article>
            <span>Best Bid</span>
            <strong>{formatMoney(book.bestBid)}</strong>
          </article>
          <article>
            <span>Best Ask</span>
            <strong>{formatMoney(book.bestAsk)}</strong>
          </article>
          <article>
            <span>Depth</span>
            <strong>{formatCompactNumber(book.buyDepth + book.sellDepth)}</strong>
          </article>
        </div>
        <OrderBookDepth book={book} />
      </Panel>

      <Panel title="Liquidity Snapshot" subtitle="Depth-specific metrics for the selected symbol without cross-tab trading noise">
        <div className="risk-cards">
          <article>
            <span>Selected Symbol</span>
            <strong>{book.symbol}</strong>
          </article>
          <article>
            <span>Spread</span>
            <strong>{formatMoney(book.bestAsk - book.bestBid)}</strong>
          </article>
          <article>
            <span>Total Depth</span>
            <strong>{formatCompactNumber(book.buyDepth + book.sellDepth)}</strong>
          </article>
        </div>
        <div className="detail-list">
          <div>
            <span>Bid Side Orders</span>
            <strong>{formatCompactNumber(book.buyDepth)}</strong>
          </div>
          <div>
            <span>Ask Side Orders</span>
            <strong>{formatCompactNumber(book.sellDepth)}</strong>
          </div>
          <div>
            <span>Active Symbols</span>
            <strong>{formatCompactNumber(state.activeSymbols.length)}</strong>
          </div>
          <div>
            <span>Stream State</span>
            <strong>{liveState.orderBook}</strong>
          </div>
        </div>
      </Panel>
    </section>
  );
}
