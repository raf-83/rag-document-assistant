"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getBackendStatus } from "@/services/health-api";

export default function HomePage() {
  const [status, setStatus] = useState("Sprawdzanie połączenia...");
  const [error, setError] = useState("");

  useEffect(() => {
    getBackendStatus()
      .then((result) => setStatus(result))
      .catch(() => setError("Nie udało się połączyć z backendem."));
  }, []);

  return (
    <main className="flex min-h-screen items-center px-6 py-12 sm:px-10">
      <div className="mx-auto w-full max-w-5xl">
        <header className="flex items-center gap-3" aria-label="RAG Document Assistant">
          <span
            className="flex size-10 items-center justify-center rounded-xl bg-blue-600 text-sm font-bold text-white shadow-sm"
            aria-hidden="true"
          >
            DV
          </span>
          <span className="text-base font-semibold tracking-tight">
            RAG Document Assistant
          </span>
        </header>

        <section className="mt-16 max-w-3xl sm:mt-24">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-700">
            Bezpieczna przestrzeń na dokumenty
          </p>
          <h1 className="mt-5 text-4xl font-bold tracking-tight text-slate-950 sm:text-6xl">
            Dokumenty i wiedza w jednym miejscu.
          </h1>
          <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-600 sm:text-xl">
            RAG Document Assistant to aplikacja do bezpiecznego przechowywania,
            przeszukiwania i analizowania własnych dokumentów.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link className="primary-button" href="/login">Zaloguj się</Link>
            <Link className="secondary-button" href="/register">Utwórz konto</Link>
          </div>
        </section>

        <section
          className="mt-16 max-w-xl rounded-2xl border border-slate-200 bg-white/80 p-5 shadow-sm backdrop-blur sm:p-6"
          aria-labelledby="backend-status-heading"
        >
          <div className="flex items-start gap-4">
            <span
              className={`mt-1 size-2.5 shrink-0 rounded-full ${
                error ? "bg-red-500" : "bg-emerald-500"
              }`}
              aria-hidden="true"
            />
            <div>
              <h2
                id="backend-status-heading"
                className="font-semibold text-slate-900"
              >
                Status backendu
              </h2>
              <p
                className={`mt-1 text-sm leading-6 ${
                  error ? "text-red-700" : "text-slate-600"
                }`}
                aria-live="polite"
              >
                {error || status}
              </p>
            </div>
          </div>
        </section>

        <footer className="mt-16 border-t border-slate-200 pt-6 text-sm text-slate-500">
          RAG Document Assistant · frontend aplikacji
        </footer>
      </div>
    </main>
  );
}
