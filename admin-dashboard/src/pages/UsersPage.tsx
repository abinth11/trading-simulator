import { useMemo, useState } from "react";
import DataTable from "../components/DataTable";
import Panel from "../components/Panel";
import StatusBadge from "../components/StatusBadge";
import type { DashboardData } from "../hooks/useDashboardData";
import type { TableColumn } from "../types";
import { formatMoney, formatSignedMoney, mapStatusTone } from "../utils/format";

type UserRow = DashboardData["mergedUsers"][number];

const userColumns: TableColumn<UserRow>[] = [
  { key: "username", label: "Username", sortable: true },
  { key: "role", label: "Role", className: "users-role", render: (row) => <StatusBadge value={row.role} tone={mapStatusTone(row.role)} /> },
  { key: "isActive", label: "Status", render: (row) => <StatusBadge value={row.isActive ? "ACTIVE" : "INACTIVE"} tone={row.isActive ? "positive" : "critical"} /> },
  { key: "cashBalance", label: "Cash", className: "users-cash", align: "right", render: (row) => formatMoney(row.cashBalance), sortable: true, sortValue: (row) => row.cashBalance },
  { key: "portfolioValue", label: "Portfolio", className: "users-portfolio", align: "right", render: (row) => formatMoney(row.portfolioValue), sortable: true, sortValue: (row) => row.portfolioValue },
  { key: "totalUnrealizedPnl", label: "PnL", className: "users-pnl", align: "right", render: (row) => <span className={row.totalUnrealizedPnl >= 0 ? "positive-text" : "negative-text"}>{formatSignedMoney(row.totalUnrealizedPnl)}</span>, sortable: true, sortValue: (row) => row.totalUnrealizedPnl }
];

export default function UsersPage({ data }: { data: DashboardData }) {
  const { mergedUsers, selectUser } = data;
  const [search, setSearch] = useState("");

  const filteredUsers = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return mergedUsers;
    return mergedUsers.filter((user) =>
      [user.username, user.email, user.role].some((value) => value.toLowerCase().includes(query))
    );
  }, [mergedUsers, search]);

  return (
    <section className="dashboard-grid users-tab-grid">
      <Panel
        title="User Registry"
        subtitle="Search accounts and select View to inspect portfolio and holdings"
        action={
          <input
            className="table-search"
            aria-label="Search users by username, email, or role"
            placeholder="Search users"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        }
      >
        <DataTable
          columns={userColumns}
          rows={filteredUsers}
          rowKey={(row) => row.id}
          emptyMessage={search.trim() ? "No users match this search." : "No user accounts are available."}
          rowAction={{ label: "View", onSelect: selectUser }}
        />
      </Panel>

      <Panel title="Account Pulse" subtitle="Quick read on account health and concentration">
        <div className="leaderboard">
          {filteredUsers.slice(0, 8).map((user) => (
            <div key={user.id} className="leaderboard-row">
              <div>
                <strong>{user.username}</strong>
                <span>{user.role}</span>
              </div>
              <div>
                <strong>{formatMoney(user.portfolioValue)}</strong>
                <span className={user.totalUnrealizedPnl >= 0 ? "positive-text" : "negative-text"}>
                  {formatSignedMoney(user.totalUnrealizedPnl)}
                </span>
              </div>
            </div>
          ))}
        </div>
      </Panel>
    </section>
  );
}
