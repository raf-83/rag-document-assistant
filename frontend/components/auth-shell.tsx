import Link from "next/link";

export function AuthShell({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <main className="grid min-h-screen lg:grid-cols-[1fr_1.05fr]">
      <section className="hidden bg-slate-950 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <Link href="/" className="flex items-center gap-3 font-semibold">
          <span className="flex size-10 items-center justify-center rounded-xl bg-blue-500 text-sm font-bold">DV</span>
          RAG Document Assistant
        </Link>
        <div className="max-w-lg">
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-blue-300">Twój asystent dokumentów</p>
          <h2 className="mt-5 text-4xl font-semibold leading-tight">Dokumenty, których treść możesz naprawdę wykorzystać.</h2>
          <p className="mt-5 leading-7 text-slate-300">Bezpieczne przechowywanie, inteligentne wyszukiwanie i odpowiedzi oparte na Twoich dokumentach.</p>
        </div>
        <p className="text-sm text-slate-500">RAG Document Assistant</p>
      </section>

      <section className="flex items-center justify-center px-5 py-12 sm:px-10">
        <div className="w-full max-w-md">
          <Link href="/" className="mb-12 flex items-center gap-3 font-semibold lg:hidden">
            <span className="flex size-9 items-center justify-center rounded-xl bg-blue-600 text-xs font-bold text-white">DV</span>
            RAG Document Assistant
          </Link>
          <p className="text-sm font-semibold uppercase tracking-[0.16em] text-blue-700">{eyebrow}</p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">{title}</h1>
          <p className="mt-3 leading-7 text-slate-600">{description}</p>
          <div className="mt-8">{children}</div>
        </div>
      </section>
    </main>
  );
}
