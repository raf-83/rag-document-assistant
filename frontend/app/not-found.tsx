import Link from "next/link";

export default function NotFound() {
  return <main className="flex min-h-screen items-center justify-center px-5"><div className="max-w-md text-center"><p className="text-sm font-bold text-blue-700">404</p><h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-950">Nie znaleziono strony</h1><p className="mt-3 text-slate-600">Podany adres nie prowadzi do istniejącego widoku.</p><Link className="primary-button mt-7" href="/">Wróć na stronę główną</Link></div></main>;
}
