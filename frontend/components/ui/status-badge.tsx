import type { DocumentStatus } from "@/features/documents/types";

const statusConfig: Record<DocumentStatus, { label: string; className: string }> = {
  UPLOADED: { label: "Przesłany", className: "status-neutral" },
  TEXT_PROCESSING: { label: "Ekstrakcja tekstu", className: "status-progress" },
  CONTENT_READY: { label: "Treść gotowa", className: "status-progress" },
  CHUNKING: { label: "Dzielenie treści", className: "status-progress" },
  READY: { label: "Gotowy", className: "status-success" },
  FAILED: { label: "Błąd", className: "status-error" },
};

export function StatusBadge({ status }: { status: DocumentStatus }) {
  const config = statusConfig[status];
  return <span className={`status-badge ${config.className}`}>{config.label}</span>;
}

export function statusLabel(status: DocumentStatus): string {
  return statusConfig[status].label;
}
