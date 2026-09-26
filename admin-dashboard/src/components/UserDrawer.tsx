import Panel from "./Panel";
import StatusBadge from "./StatusBadge";
import type { Holding } from "../types";
import { useEffect, useRef, type KeyboardEvent } from "react";
import { formatMoney, formatPercent, formatSignedMoney, mapStatusTone } from "../utils/format";

interface UserDrawerModel {
  id: string;
  email: string;
  username: string;
  cashBalance: number;
  role: string;
  isActive: boolean;
  orderCount: number;
  portfolioValue: number;
  totalUnrealizedPnl: number;
  holdings: Holding[];
}

interface UserDrawerProps {
  user: UserDrawerModel | null;
  loading: boolean;
  onClose: () => void;
}

export default function UserDrawer({ user, loading, onClose }: UserDrawerProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (user) {
      openerRef.current = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
      closeButtonRef.current?.focus();
      return;
    }

    openerRef.current?.focus();
    openerRef.current = null;
  }, [Boolean(user)]);

  if (!user) return null;

  function handleDialogKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
      return;
    }

    if (event.key !== "Tab") return;
    const focusable = event.currentTarget.querySelectorAll<HTMLElement>(
      'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), summary, [tabindex]:not([tabindex="-1"])'
    );
    if (!focusable.length) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="drawer-backdrop" onClick={onClose} onKeyDown={handleDialogKeyDown}>
      <section
        className="drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="user-drawer-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="drawer-header">
          <div>
            <div className="eyebrow">User Lens</div>
            <h2 id="user-drawer-title">{user.username}</h2>
            <p>{user.email}</p>
          </div>
          <button ref={closeButtonRef} className="ghost-button" onClick={onClose} type="button">
            Close
          </button>
        </div>

        <div className="drawer-grid">
          <Panel title="Identity" subtitle="Role, account state, and available cash">
            <div className="detail-list">
              <div><span>Role</span><StatusBadge value={user.role} tone={mapStatusTone(user.role)} /></div>
              <div><span>Status</span><StatusBadge value={user.isActive ? "ACTIVE" : "INACTIVE"} tone={user.isActive ? "positive" : "critical"} /></div>
              <div><span>Cash Balance</span><strong>{formatMoney(user.cashBalance)}</strong></div>
              <div><span>Recent Orders</span><strong>{user.orderCount}</strong></div>
            </div>
          </Panel>

          <Panel title="Portfolio" subtitle="Live valuation from portfolio-service">
            <div className="detail-list">
              <div><span>Total Value</span><strong>{formatMoney(user.portfolioValue)}</strong></div>
              <div><span>Unrealized PnL</span><strong className={user.totalUnrealizedPnl >= 0 ? "positive-text" : "negative-text"}>{formatSignedMoney(user.totalUnrealizedPnl)}</strong></div>
              <div><span>Holdings</span><strong>{user.holdings.length}</strong></div>
              <div><span>Mode</span><strong>{user.holdings.length ? "Invested" : "Cash-heavy"}</strong></div>
            </div>
          </Panel>
        </div>

        <Panel title="Holdings" subtitle="Concentration, price basis, and unrealized performance">
          {loading ? (
            <div className="empty-state">Loading live portfolio...</div>
          ) : user.holdings.length ? (
            <div className="holding-list">
              {user.holdings.map((holding) => (
                <div className="holding-item" key={holding.symbol}>
                  <div>
                    <strong>{holding.symbol}</strong>
                    <span>{holding.quantity} units</span>
                  </div>
                  <div>
                    <strong>{formatMoney(holding.marketValue)}</strong>
                    <span className={holding.unrealizedPnl >= 0 ? "positive-text" : "negative-text"}>
                      {formatSignedMoney(holding.unrealizedPnl)} ({formatPercent(holding.unrealizedPnlPct)})
                    </span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty-state">No holdings for this account.</div>
          )}
        </Panel>
      </section>
    </div>
  );
}
