"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { StatusBadge, statusLabel } from "@/components/ui/status-badge";
import { errorMessage } from "@/lib/api/client";
import { formatDate, formatFileSize } from "@/lib/format";
import { getDocuments } from "./api";
import { DOCUMENT_STATUSES, type DocumentResponse, type DocumentStatus } from "./types";

export function DocumentList() {
  const [status, setStatus] = useState<DocumentStatus | "">("");
  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try { setDocuments(await getDocuments(status || undefined)); }
    catch (reason) { setError(errorMessage(reason)); }
    finally { setLoading(false); }
  }, [status]);

  useEffect(() => { void load(); }, [load]);

  return (
    <div>
      <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
        <div><p className="text-sm font-semibold text-blue-700">Repozytorium</p><h1 className="page-heading mt-1">Dokumenty</h1><p className="page-description">Przeglądaj pliki i śledź stan ich przetwarzania.</p></div>
        <Link className="primary-button" href="/documents/upload">Dodaj dokument</Link>
      </div>

      <div className="mt-8 flex max-w-xs flex-col"><label className="field-label" htmlFor="status">Status</label><select className="field-input" id="status" value={status} onChange={(event) => setStatus(event.target.value as DocumentStatus | "")}><option value="">Wszystkie statusy</option>{DOCUMENT_STATUSES.map((value) => <option value={value} key={value}>{statusLabel(value)}</option>)}</select></div>

      <div className="mt-6">
        {loading ? <LoadingState label="Ładowanie dokumentów..." /> : error ? <ErrorState message={error} onRetry={load} /> : documents.length === 0 ? <EmptyState title="Brak dokumentów" description={status ? "Żaden dokument nie ma wybranego statusu." : "Dodaj pierwszy plik, aby rozpocząć pracę."} action={!status ? <Link className="primary-button" href="/documents/upload">Dodaj dokument</Link> : undefined} /> : (
          <div className="panel overflow-hidden"><div className="hidden grid-cols-[minmax(0,2fr)_1fr_1fr_auto] gap-4 border-b border-slate-200 bg-slate-50 px-6 py-3 text-xs font-semibold uppercase tracking-wide text-slate-500 md:grid"><span>Dokument</span><span>Typ</span><span>Aktualizacja</span><span>Status</span></div><div className="divide-y divide-slate-100">{documents.map((document) => <Link href={`/documents/${document.id}`} key={document.id} className="grid gap-3 px-5 py-5 transition hover:bg-slate-50 md:grid-cols-[minmax(0,2fr)_1fr_1fr_auto] md:items-center md:gap-4 md:px-6"><div className="min-w-0"><p className="truncate font-semibold text-slate-900">{document.title}</p><p className="mt-1 truncate text-sm text-slate-500">{document.storedFile.originalFileName} · {formatFileSize(document.storedFile.sizeInBytes)}</p></div><p className="text-sm text-slate-600">{document.documentTypeCode}</p><p className="text-sm text-slate-500">{formatDate(document.updatedAt)}</p><div><StatusBadge status={document.status} /></div></Link>)}</div></div>
        )}
      </div>
    </div>
  );
}
