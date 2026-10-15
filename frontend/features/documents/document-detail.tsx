"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/ui/feedback";
import { StatusBadge, statusLabel } from "@/components/ui/status-badge";
import { errorMessage } from "@/lib/api/client";
import { formatDate, formatFileSize } from "@/lib/format";
import { downloadDocument, getDocumentContent } from "./api";
import type { DocumentContentResponse } from "./types";
import { useDocumentPolling } from "./use-document-polling";

const steps = ["UPLOADED", "TEXT_PROCESSING", "CONTENT_READY", "CHUNKING", "READY"] as const;

export function DocumentDetail({ documentId }: { documentId: string }) {
  const { document, loading, error, reload } = useDocumentPolling(documentId);
  const [content, setContent] = useState<DocumentContentResponse | null>(null);
  const [contentError, setContentError] = useState("");
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState("");

  useEffect(() => {
    if (document?.status !== "READY") return;
    let active = true;
    getDocumentContent(document.id)
      .then((result) => { if (active) setContent(result); })
      .catch((reason) => { if (active) setContentError(errorMessage(reason)); });
    return () => { active = false; };
  }, [document?.id, document?.status]);

  if (loading && !document) return <LoadingState label="Ładowanie dokumentu..." />;
  if (error && !document) return <ErrorState message={error} onRetry={reload} />;
  if (!document) return null;

  const currentStep = steps.indexOf(document.status as (typeof steps)[number]);

  async function download() {
    setDownloading(true); setDownloadError("");
    try { await downloadDocument(document!); }
    catch (reason) { setDownloadError(errorMessage(reason)); }
    finally { setDownloading(false); }
  }

  return (
    <div>
      <Link className="text-button -ml-2" href="/documents">← Wróć do dokumentów</Link>
      <div className="mt-4 flex flex-col justify-between gap-5 sm:flex-row sm:items-start"><div className="min-w-0"><div className="flex flex-wrap items-center gap-3"><h1 className="page-heading break-words">{document.title}</h1><StatusBadge status={document.status} /></div><p className="mt-2 break-all text-sm text-slate-500">{document.storedFile.originalFileName}</p></div><div className="flex shrink-0 flex-wrap gap-3"><button className="secondary-button" type="button" onClick={download} disabled={downloading}>{downloading ? "Pobieranie..." : "Pobierz plik"}</button>{document.status === "READY" ? <Link className="primary-button" href={`/documents/${document.id}/chat`}>Zapytaj dokument</Link> : <span className="primary-button cursor-not-allowed opacity-50" title="Chat jest dostępny dla gotowego dokumentu">Zapytaj dokument</span>}</div></div>
      {downloadError && <p className="mt-4 text-sm text-red-700" role="alert">{downloadError}</p>}

      <section className="panel mt-8 p-5 sm:p-6"><h2 className="font-semibold text-slate-950">Przetwarzanie</h2>{document.status === "FAILED" ? <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">Przetwarzanie dokumentu zakończyło się błędem. Backend nie udostępnia szczegółowej przyczyny.</div> : <ol className="mt-5 grid gap-3 sm:grid-cols-5">{steps.map((step, index) => <li key={step} className={`rounded-xl border p-3 text-xs font-semibold ${index <= currentStep ? "border-blue-200 bg-blue-50 text-blue-800" : "border-slate-200 bg-slate-50 text-slate-400"}`}><span className="mb-2 block size-2 rounded-full bg-current" />{statusLabel(step)}</li>)}</ol>} {!(["READY", "FAILED"].includes(document.status)) && <p className="mt-4 flex items-center gap-2 text-sm text-slate-500"><span className="spinner" /> Status jest odświeżany automatycznie.</p>}</section>

      <div className="mt-6 grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
        <section className="panel p-5 sm:p-6"><h2 className="font-semibold text-slate-950">Informacje</h2><dl className="mt-5 space-y-4 text-sm">{[["Typ", document.documentTypeCode], ["Format", document.storedFile.mimeType], ["Rozmiar", formatFileSize(document.storedFile.sizeInBytes)], ["Dodano", formatDate(document.createdAt)], ["Aktualizacja", formatDate(document.updatedAt)]].map(([label, value]) => <div className="flex justify-between gap-4 border-b border-slate-100 pb-3 last:border-0" key={label}><dt className="text-slate-500">{label}</dt><dd className="text-right font-medium text-slate-800">{value}</dd></div>)}</dl></section>
        <section className="panel min-h-80 p-5 sm:p-6"><div className="flex items-center justify-between gap-3"><div><h2 className="font-semibold text-slate-950">Wyodrębniona treść</h2>{content && <p className="mt-1 text-xs text-slate-500">{content.characterCount?.toLocaleString("pl-PL") ?? 0} znaków{content.pageCount ? ` · ${content.pageCount} stron` : ""}</p>}</div></div>{document.status !== "READY" ? <p className="mt-6 text-sm leading-6 text-slate-500">Treść będzie dostępna po zakończeniu przetwarzania dokumentu.</p> : contentError ? <p className="mt-6 text-sm text-red-700" role="alert">{contentError}</p> : !content ? <div className="mt-8 flex items-center gap-2 text-sm text-slate-500"><span className="spinner" /> Ładowanie treści...</div> : <pre className="mt-5 max-h-[34rem] overflow-auto whitespace-pre-wrap break-words rounded-xl bg-slate-50 p-4 font-sans text-sm leading-7 text-slate-700">{content.sanitizedText}</pre>}</section>
      </div>
    </div>
  );
}
