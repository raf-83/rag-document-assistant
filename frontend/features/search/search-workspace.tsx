"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { EmptyState } from "@/components/ui/feedback";
import { errorMessage } from "@/lib/api/client";
import { formatDate, formatScore } from "@/lib/format";
import { getDocumentTypes } from "@/features/documents/api";
import type { DocumentTypeResponse } from "@/features/documents/types";
import { fullTextSearch, hybridSearch, semanticSearch } from "./api";
import type {
  DocumentSearchPage,
  HybridSearchResponse,
  SearchFilters,
  SemanticSearchResponse,
} from "./types";

type SearchMode = "hybrid" | "semantic" | "fulltext";
type Results = HybridSearchResponse | SemanticSearchResponse | DocumentSearchPage;

function isoStart(value: string): string | undefined {
  return value ? `${value}T00:00:00.000Z` : undefined;
}

function isoEnd(value: string): string | undefined {
  return value ? `${value}T23:59:59.999Z` : undefined;
}

export function SearchWorkspace() {
  const [mode, setMode] = useState<SearchMode>("hybrid");
  const [types, setTypes] = useState<DocumentTypeResponse[]>([]);
  const [results, setResults] = useState<Results | null>(null);
  const [lastQuery, setLastQuery] = useState("");
  const [page, setPage] = useState(0);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    getDocumentTypes().then(setTypes).catch(() => setTypes([]));
  }, []);

  async function runSearch(filters: SearchFilters, targetPage = 0) {
    setPending(true);
    setError("");
    try {
      const response = mode === "fulltext"
        ? await fullTextSearch(filters.query, targetPage)
        : mode === "semantic"
          ? await semanticSearch(filters)
          : await hybridSearch(filters);
      setResults(response);
      setLastQuery(filters.query);
      setPage(targetPage);
    } catch (reason) {
      setError(errorMessage(reason));
      setResults(null);
    } finally {
      setPending(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const query = String(data.get("query") ?? "").trim();
    if (query.length < 2) { setError("Zapytanie musi mieć co najmniej 2 znaki."); return; }
    const filters: SearchFilters = {
      query,
      limit: Number(data.get("limit") ?? 10),
      documentTypeId: String(data.get("documentTypeId") ?? "") || undefined,
      createdFrom: isoStart(String(data.get("createdFrom") ?? "")),
      createdTo: isoEnd(String(data.get("createdTo") ?? "")),
    };
    await runSearch(filters, 0);
  }

  const resultCount = results
    ? "totalElements" in results ? results.totalElements : results.resultCount
    : 0;

  return (
    <div>
      <div><p className="text-sm font-semibold text-blue-700">Odkrywaj wiedzę</p><h1 className="page-heading mt-1">Wyszukiwanie</h1><p className="page-description">Znajdź dokumenty po słowach kluczowych albo znaczeniu ich treści.</p></div>

      <form className="panel mt-8 p-5 sm:p-6" onSubmit={submit}>
        <fieldset><legend className="field-label">Tryb wyszukiwania</legend><div className="grid gap-2 sm:grid-cols-3">{([
          ["hybrid", "Hybrydowe", "Znaczenie i słowa kluczowe"],
          ["semantic", "Semantyczne", "Podobieństwo znaczenia"],
          ["fulltext", "Pełnotekstowe", "Dokładne słowa i frazy"],
        ] as const).map(([value, label, description]) => <label key={value} className={`cursor-pointer rounded-xl border p-3 transition ${mode === value ? "border-blue-500 bg-blue-50" : "border-slate-200 hover:bg-slate-50"}`}><input className="sr-only" type="radio" name="mode" value={value} checked={mode === value} onChange={() => { setMode(value); setResults(null); setError(""); }} /><span className="block text-sm font-semibold text-slate-900">{label}</span><span className="mt-0.5 block text-xs text-slate-500">{description}</span></label>)}</div></fieldset>

        <div className="mt-5 flex flex-col gap-3 sm:flex-row"><div className="flex-1"><label className="sr-only" htmlFor="query">Zapytanie</label><input className="field-input" id="query" name="query" minLength={2} maxLength={1000} required placeholder="Czego szukasz w swoich dokumentach?" /></div><button className="primary-button sm:min-w-32" type="submit" disabled={pending}>{pending ? "Szukam..." : "Szukaj"}</button></div>

        {mode !== "fulltext" && <div className="mt-4 grid gap-4 border-t border-slate-100 pt-4 sm:grid-cols-2 lg:grid-cols-4"><div><label className="field-label" htmlFor="documentTypeId">Typ dokumentu</label><select className="field-input" id="documentTypeId" name="documentTypeId"><option value="">Wszystkie</option>{types.filter((type) => type.active).map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></div><div><label className="field-label" htmlFor="createdFrom">Dodane od</label><input className="field-input" id="createdFrom" name="createdFrom" type="date" /></div><div><label className="field-label" htmlFor="createdTo">Dodane do</label><input className="field-input" id="createdTo" name="createdTo" type="date" /></div><div><label className="field-label" htmlFor="limit">Liczba wyników</label><select className="field-input" id="limit" name="limit" defaultValue="10"><option value="5">5</option><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></div></div>}
      </form>

      {error && <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert">{error}</div>}

      <section className="mt-8" aria-live="polite">
        {!results && !pending && !error ? <EmptyState title="Wpisz zapytanie" description="Tryb hybrydowy łączy wyszukiwanie semantyczne i pełnotekstowe." /> : results && resultCount === 0 ? <EmptyState title="Brak wyników" description={`Nie znaleziono dokumentów dla zapytania „${lastQuery}”.`} /> : results ? <><div className="mb-4 flex items-center justify-between"><h2 className="font-semibold text-slate-950">Wyniki dla „{lastQuery}”</h2><span className="text-sm text-slate-500">{resultCount} wyników</span></div><ResultList mode={mode} results={results} />{"totalPages" in results && results.totalPages > 1 && <div className="mt-5 flex items-center justify-center gap-3"><button className="secondary-button" disabled={page === 0 || pending} onClick={() => void runSearch({ query: lastQuery }, page - 1)}>Poprzednia</button><span className="text-sm text-slate-500">Strona {page + 1} z {results.totalPages}</span><button className="secondary-button" disabled={page + 1 >= results.totalPages || pending} onClick={() => void runSearch({ query: lastQuery }, page + 1)}>Następna</button></div>}</> : null}
      </section>
    </div>
  );
}

function ResultList({ mode, results }: { mode: SearchMode; results: Results }) {
  if (mode === "fulltext" && "totalElements" in results) {
    return <div className="space-y-3">{results.results.map((item) => <Link className="panel block p-5 transition hover:border-blue-200 hover:shadow-sm" href={`/documents/${item.documentId}`} key={item.documentId}><div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-semibold text-slate-950">{item.title}</h3><p className="mt-1 text-xs font-medium uppercase tracking-wide text-slate-500">{item.documentType} · {formatDate(item.updatedAt)}</p></div><span className="rounded-lg bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">trafność {Number(item.rank).toFixed(3)}</span></div><p className="mt-4 line-clamp-3 text-sm leading-7 text-slate-600">{item.contentSnippet ?? item.titleSnippet ?? "Dopasowanie w dokumencie"}</p></Link>)}</div>;
  }

  if (mode === "semantic" && "results" in results && !results.results.some((item) => "chunks" in item)) {
    const semantic = results as SemanticSearchResponse;
    return <div className="space-y-3">{semantic.results.map((item) => <Link className="panel block p-5 transition hover:border-blue-200 hover:shadow-sm" href={`/documents/${item.documentId}`} key={item.chunkId}><div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-semibold text-slate-950">{item.documentTitle}</h3><p className="mt-1 text-xs font-medium uppercase tracking-wide text-slate-500">{item.documentTypeName} · fragment {item.chunkIndex + 1}</p></div><span className="rounded-lg bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700">{formatScore(item.score)}</span></div><p className="mt-4 line-clamp-3 text-sm leading-7 text-slate-600">{item.textPreview}</p></Link>)}</div>;
  }

  const hybrid = results as HybridSearchResponse;
  return <div className="space-y-4">{hybrid.results.map((item) => <article className="panel p-5" key={item.documentId}><div className="flex flex-wrap items-start justify-between gap-3"><div><Link className="font-semibold text-slate-950 hover:text-blue-700" href={`/documents/${item.documentId}`}>{item.documentTitle}</Link><p className="mt-1 text-xs font-medium uppercase tracking-wide text-slate-500">{item.documentTypeName} · {item.matchedChunkCount} dopasowanych fragmentów</p></div><span className="rounded-lg bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700">{formatScore(item.score)}</span></div><div className="mt-4 space-y-2">{item.chunks.map((chunk) => <div className="rounded-xl bg-slate-50 p-4" key={chunk.chunkId}><div className="flex items-center justify-between gap-3 text-xs"><span className="font-semibold text-slate-500">Fragment {chunk.chunkIndex + 1} · {chunk.matchType.replaceAll("_", " ")}</span><span className="font-semibold text-slate-600">{formatScore(chunk.score)}</span></div><p className="mt-2 line-clamp-3 text-sm leading-6 text-slate-600">{chunk.textPreview}</p></div>)}</div></article>)}</div>;
}
