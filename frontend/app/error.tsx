"use client";

export default function GlobalError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return <main className="flex min-h-screen items-center justify-center px-5"><div className="panel max-w-md p-7 text-center"><div className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-red-50 text-xl text-red-700">!</div><h1 className="mt-4 text-xl font-bold text-slate-950">Coś poszło nie tak</h1><p className="mt-2 text-sm leading-6 text-slate-600">Nie udało się wyświetlić tej części aplikacji.</p><button className="primary-button mt-6" type="button" onClick={reset}>Spróbuj ponownie</button></div></main>;
}
