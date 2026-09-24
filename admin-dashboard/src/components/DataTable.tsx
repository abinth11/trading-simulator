import { useMemo, useState } from "react";
import type { TableColumn } from "../types";

interface DataTableProps<TRow> {
  columns: TableColumn<TRow>[];
  rows: TRow[];
  rowKey: (row: TRow) => string;
}

export default function DataTable<TRow>({ columns, rows, rowKey }: DataTableProps<TRow>) {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("desc");

  const sortedRows = useMemo(() => {
    if (!sortKey) return rows;

    const selectedColumn = columns.find((column) => String(column.key) === sortKey);
    if (!selectedColumn) return rows;

    const sorted = [...rows].sort((a, b) => {
      const aValue = selectedColumn.sortValue
        ? selectedColumn.sortValue(a)
        : (a[sortKey as keyof TRow] as string | number | undefined) ?? "";
      const bValue = selectedColumn.sortValue
        ? selectedColumn.sortValue(b)
        : (b[sortKey as keyof TRow] as string | number | undefined) ?? "";

      if (typeof aValue === "number" && typeof bValue === "number") {
        return sortDirection === "asc" ? aValue - bValue : bValue - aValue;
      }

      return sortDirection === "asc"
        ? String(aValue).localeCompare(String(bValue))
        : String(bValue).localeCompare(String(aValue));
    });

    return sorted;
  }, [columns, rows, sortDirection, sortKey]);

  function toggleSort(key: string): void {
    if (sortKey === key) {
      setSortDirection((current) => (current === "asc" ? "desc" : "asc"));
      return;
    }
    setSortKey(key);
    setSortDirection("desc");
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={String(column.key)} className={column.align === "right" ? "align-right" : undefined}>
                {column.sortable ? (
                  <button
                    className={`table-sort ${sortKey === String(column.key) ? "active" : ""}`}
                    onClick={() => toggleSort(String(column.key))}
                    type="button"
                  >
                    {column.label}
                    <span>{sortKey === String(column.key) ? (sortDirection === "asc" ? "↑" : "↓") : "↕"}</span>
                  </button>
                ) : (
                  column.label
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sortedRows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((column) => (
                <td
                  key={String(column.key)}
                  className={`${column.className ?? ""} ${column.align === "right" ? "align-right" : ""}`.trim()}
                >
                  {column.render ? column.render(row) : String(row[column.key as keyof TRow] ?? "")}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
