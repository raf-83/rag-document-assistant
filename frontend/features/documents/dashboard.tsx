"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/ui/feedback";
import { StatusBadge } from "@/components/ui/status-badge";
import { errorMessage } from "@/lib/api/client";
import { formatDate } from "@/lib/format";
import { getDocuments } from "./api";
import type { DocumentResponse } from "./types";

export function Dashboard() {
  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setDocuments(await getDocuments());
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  if (loading) return <LoadingState label="Ładowanie dashboardu..." />;
  if (error) return <ErrorState message={error} onRetry={load} />;

  const processing = documents.filter((item) => !["READY", "FAILED"].includes(item.status)).length;
  const ready = documents.filter((item) => item.status === "READY").length;
  const failed = documents.filter((item) => item.status === "FAILED").length;
  const recent = [...documents].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt)).slice(0, 5);

  return (
    <div>
      <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
        <div><p className="text-sm font-semibold text-blue-700">Przegląd</p><h1 className="page-heading mt-1">Dashboard</h1><p className="page-description">Najważniejsze informacje o Twoim repozytorium.</p></div>
        <Link className="primary-button" href="/documents/upload">Dodaj dokument</Link>
      </div>

      <section className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Statystyki dokumentów">
        {[
          ["Wszystkie dokumenty", documents.length, "bg-slate-100 text-slate-700"],
          ["Gotowe", ready, "bg-emerald-50 text-emerald-700"],
          ["Przetwarzane", processing, "bg-blue-50 text-blue-700"],
          ["Błędy", failed, "bg-red-50 text-red-700"],
        ].map(([label, value, color]) => (
          <div className="panel p-5" key={String(label)}><span className={`inline-flex rounded-lg px-2.5 py-1 text-xs font-semibold ${color}`}>{label}</span><p className="mt-4 text-3xl font-bold tracking-tight text-slate-950">{value}</p></div>
        ))}
      </section>

      <section className="panel mt-8 overflow-hidden">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4 sm:px-6"><div><h2 className="font-semibold text-slate-950">Ostatnie dokumenty</h2><p className="mt-1 text-sm text-slate-500">Ostatnio aktualizowane pozycje</p></div><Link className="text-button" href="/documents">Zobacz wszystkie</Link></div>
        {recent.length === 0 ? <div className="p-8 text-center text-sm text-slate-500">Nie masz jeszcze żadnych dokumentów.</div> : (
          <div className="divide-y divide-slate-100">{recent.map((document) => <Link href={`/documents/${document.id}`} key={document.id} className="flex items-center justify-between gap-4 px-5 py-4 transition hover:bg-slate-50 sm:px-6"><div className="min-w-0"><p className="truncate font-medium text-slate-900">{document.title}</p><p className="mt-1 truncate text-sm text-slate-500">{document.storedFile.originalFileName} · {formatDate(document.updatedAt)}</p></div><StatusBadge status={document.status} /></Link>)}</div>
        )}
      </section>
    </div>
  );
}
