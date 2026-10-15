"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";

const navigation = [
  { href: "/dashboard", label: "Dashboard", icon: "home" },
  { href: "/documents", label: "Dokumenty", icon: "file" },
  { href: "/documents/upload", label: "Dodaj dokument", icon: "plus" },
  { href: "/search", label: "Wyszukiwanie", icon: "search" },
] as const;

function NavIcon({ name }: { name: (typeof navigation)[number]["icon"] }) {
  const paths = {
    home: <path d="M3 10.5 12 3l9 7.5V21a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z" />,
    file: <path d="M6 2h8l4 4v16H6Zm8 0v5h5M9 12h6M9 16h6" />,
    plus: <path d="M12 5v14M5 12h14" />,
    search: <path d="m21 21-4.4-4.4M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z" />,
  };

  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      {paths[name]}
    </svg>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [loggingOut, setLoggingOut] = useState(false);

  async function logout() {
    setLoggingOut(true);
    try {
      await fetch("/api/session/logout", { method: "POST" });
    } finally {
      router.replace("/login");
      router.refresh();
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 md:grid md:grid-cols-[16rem_1fr]">
      <aside className="border-b border-slate-200 bg-slate-950 text-slate-100 md:sticky md:top-0 md:h-screen md:border-b-0 md:border-r">
        <div className="flex h-full flex-col">
          <div className="flex h-16 items-center gap-3 px-5 md:h-20">
            <span className="flex size-9 items-center justify-center rounded-xl bg-blue-500 text-xs font-bold text-white">DV</span>
            <div>
              <p className="font-semibold tracking-tight">RAG Document Assistant</p>
              <p className="text-xs text-slate-400">Asystent dokumentów</p>
            </div>
          </div>

          <nav className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-1 md:flex-col md:overflow-visible md:py-4" aria-label="Główna nawigacja">
            {navigation.map((item) => {
              const active =
                pathname === item.href ||
                (item.href !== "/dashboard" && pathname.startsWith(`${item.href}/`));
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`nav-link ${active ? "nav-link-active" : ""}`}
                >
                  <span className="size-5"><NavIcon name={item.icon} /></span>
                  <span className="whitespace-nowrap">{item.label}</span>
                </Link>
              );
            })}
          </nav>

          <div className="hidden border-t border-slate-800 p-3 md:block">
            <button className="nav-link w-full" type="button" onClick={logout} disabled={loggingOut}>
              <span className="size-5" aria-hidden="true">↗</span>
              {loggingOut ? "Wylogowywanie..." : "Wyloguj się"}
            </button>
          </div>
        </div>
      </aside>

      <div className="min-w-0">
        <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-5 md:px-8">
          <p className="text-sm font-medium text-slate-500">Prywatny obszar dokumentów</p>
          <button className="text-button md:hidden" type="button" onClick={logout} disabled={loggingOut}>
            Wyloguj
          </button>
        </header>
        <main className="mx-auto w-full max-w-7xl p-5 md:p-8 lg:p-10">{children}</main>
      </div>
    </div>
  );
}
