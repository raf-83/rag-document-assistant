"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ErrorState, LoadingState } from "@/components/ui/feedback";
import { StatusBadge } from "@/components/ui/status-badge";
import { getDocumentContent } from "@/features/documents/api";
import type { DocumentContentResponse } from "@/features/documents/types";
import { useDocumentPolling } from "@/features/documents/use-document-polling";
import { errorMessage } from "@/lib/api/client";
import { formatScore } from "@/lib/format";
import { askDocument } from "./api";
import type { LocalChatMessage } from "./types";

export function ChatWorkspace({ documentId }: { documentId: string }) {
  const { document, loading, error, reload } = useDocumentPolling(documentId);
  const [content, setContent] = useState<DocumentContentResponse | null>(null);
  const [messages, setMessages] = useState<LocalChatMessage[]>([]);
  const [pending, setPending] = useState(false);
  const [chatError, setChatError] = useState("");
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (document?.status !== "READY") return;
    getDocumentContent(document.id).then(setContent).catch(() => setContent(null));
  }, [document?.id, document?.status]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [messages, pending]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!document || document.status !== "READY") return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const question = String(data.get("question") ?? "").trim();
    if (question.length < 3) { setChatError("Pytanie musi mieć co najmniej 3 znaki."); return; }

    setChatError("");
    setPending(true);
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: "user", content: question }]);
    form.reset();

    try {
      const response = await askDocument(document.id, question);
      setMessages((current) => [...current, {
        id: crypto.randomUUID(),
        role: "assistant",
        content: response.answer,
        sources: response.sources,
        contextFound: response.contextFound,
      }]);
    } catch (reason) {
      setChatError(errorMessage(reason));
    } finally {
      setPending(false);
    }
  }

  if (loading && !document) return <LoadingState label="Przygotowywanie chatu..." />;
  if (error && !document) return <ErrorState message={error} onRetry={reload} />;
  if (!document) return null;

  return (
    <div>
      <Link className="text-button -ml-2" href={`/documents/${document.id}`}>← Wróć do dokumentu</Link>
      <div className="mt-4 flex flex-wrap items-center gap-3"><div><p className="text-sm font-semibold text-blue-700">Chat RAG</p><h1 className="page-heading mt-1">{document.title}</h1></div><StatusBadge status={document.status} /></div>
      <p className="page-description">Odpowiedzi są tworzone na podstawie fragmentów tego dokumentu. Każde pytanie jest obsługiwane niezależnie przez backend.</p>

      {document.status !== "READY" ? <div className="panel mt-8 p-6"><h2 className="font-semibold text-slate-950">Dokument nie jest jeszcze gotowy</h2><p className="mt-2 text-sm leading-6 text-slate-600">Zadawanie pytań będzie dostępne po zakończeniu przetwarzania. Status odświeża się automatycznie.</p><div className="mt-4 flex items-center gap-2 text-sm text-blue-700"><span className="spinner" /> Oczekiwanie na status READY</div></div> : (
        <div className="panel mt-8 overflow-hidden">
          <div className="h-[min(58vh,38rem)] overflow-y-auto bg-slate-50/70 p-4 sm:p-6">
            {messages.length === 0 && <div className="flex h-full items-center justify-center text-center"><div><span className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-blue-100 text-xl text-blue-700" aria-hidden="true">?</span><h2 className="mt-4 font-semibold text-slate-900">Zapytaj o treść dokumentu</h2><p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">Na przykład: „Jakie są najważniejsze terminy?” albo „Czego dotyczy ten dokument?”.</p></div></div>}
            <div className="space-y-5">{messages.map((message) => <article className={`flex ${message.role === "user" ? "justify-end" : "justify-start"}`} key={message.id}><div className={`max-w-[88%] rounded-2xl px-4 py-3 sm:max-w-[75%] ${message.role === "user" ? "rounded-br-md bg-blue-600 text-white" : "rounded-bl-md border border-slate-200 bg-white text-slate-700 shadow-sm"}`}><p className="whitespace-pre-wrap text-sm leading-7">{message.content}</p>{message.role === "assistant" && message.contextFound === false && <p className="mt-3 border-t border-slate-100 pt-3 text-xs text-amber-700">Nie znaleziono wystarczającego kontekstu w dokumencie.</p>}{message.sources && message.sources.length > 0 && <details className="mt-3 border-t border-slate-100 pt-3"><summary className="cursor-pointer text-xs font-semibold text-blue-700">Źródła ({message.sources.length})</summary><div className="mt-3 space-y-2">{message.sources.map((source) => { const excerpt = content?.sanitizedText.slice(source.startCharIndex, source.endCharIndex); return <div className="rounded-lg bg-slate-50 p-3 text-xs leading-5 text-slate-600" key={source.chunkId}><p className="font-semibold text-slate-700">Fragment {source.chunkIndex + 1}{source.score != null ? ` · ${formatScore(source.score)}` : ""}</p>{excerpt && <p className="mt-1 line-clamp-4">{excerpt}</p>}</div>; })}</div></details>}</div></article>)}</div>
            {pending && <div className="mt-5 flex justify-start"><div className="flex items-center gap-2 rounded-2xl rounded-bl-md border border-slate-200 bg-white px-4 py-3 text-sm text-slate-500"><span className="spinner" /> Analizuję dokument...</div></div>}
            <div ref={endRef} />
          </div>
          <form className="border-t border-slate-200 bg-white p-4 sm:p-5" onSubmit={submit}><label className="sr-only" htmlFor="question">Pytanie do dokumentu</label><div className="flex flex-col gap-3 sm:flex-row"><textarea className="field-input min-h-12 resize-none sm:flex-1" id="question" name="question" minLength={3} maxLength={1000} rows={2} required disabled={pending} placeholder="Zadaj pytanie dotyczące dokumentu..." /><button className="primary-button sm:self-end" type="submit" disabled={pending}>{pending ? "Wysyłanie..." : "Wyślij pytanie"}</button></div>{chatError && <p className="mt-3 text-sm text-red-700" role="alert">{chatError}</p>}</form>
        </div>
      )}
    </div>
  );
}
