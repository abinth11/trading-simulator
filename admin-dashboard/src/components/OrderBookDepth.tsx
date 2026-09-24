import type { DerivedOrderBook } from "../types";
import { formatInteger } from "../utils/format";

interface OrderBookDepthProps {
  book: DerivedOrderBook;
}

export default function OrderBookDepth({ book }: OrderBookDepthProps) {
  const maxDepth = Math.max(
    ...book.buyLadder.map((level) => level.quantity),
    ...book.sellLadder.map((level) => level.quantity),
    1
  );

  return (
    <div className="book-grid">
      <div className="book-column">
        <div className="book-heading">Bid Stack</div>
        {book.buyLadder.map((level) => (
          <div className="depth-row" key={`bid-${level.price}`}>
            <span>{level.price.toFixed(2)}</span>
            <div className="depth-bar-wrap">
              <div
                className="depth-bar buy"
                style={{ width: `${(level.quantity / maxDepth) * 100}%` }}
              />
            </div>
            <strong>{formatInteger(level.quantity)}</strong>
          </div>
        ))}
      </div>

      <div className="book-column">
        <div className="book-heading">Ask Stack</div>
        {book.sellLadder.map((level) => (
          <div className="depth-row" key={`ask-${level.price}`}>
            <span>{level.price.toFixed(2)}</span>
            <div className="depth-bar-wrap">
              <div
                className="depth-bar sell"
                style={{ width: `${(level.quantity / maxDepth) * 100}%` }}
              />
            </div>
            <strong>{formatInteger(level.quantity)}</strong>
          </div>
        ))}
      </div>
    </div>
  );
}
