"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api/client";
import { login } from "./api";
import { FormMessage } from "./form-message";

export function LoginForm({ returnTo }: { returnTo: string }) {
  const router = useRouter();
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setPending(true);
    const data = new FormData(event.currentTarget);

    try {
      await login({
        email: String(data.get("email") ?? "").trim(),
        password: String(data.get("password") ?? ""),
      });
      router.replace(returnTo);
      router.refresh();
    } catch (reason) {
      setError(errorMessage(reason));
      setPending(false);
    }
  }

  return (
    <form className="space-y-5" onSubmit={submit}>
      {error && <FormMessage kind="error">{error}</FormMessage>}
      <div>
        <label className="field-label" htmlFor="email">Adres e-mail</label>
        <input className="field-input" id="email" name="email" type="email" autoComplete="email" maxLength={320} required disabled={pending} />
      </div>
      <div>
        <div className="mb-2 flex items-center justify-between">
          <label className="field-label mb-0" htmlFor="password">Hasło</label>
          <Link className="text-sm font-semibold text-blue-700 hover:text-blue-800" href="/forgot-password">Nie pamiętasz hasła?</Link>
        </div>
        <input className="field-input" id="password" name="password" type="password" autoComplete="current-password" maxLength={100} required disabled={pending} />
      </div>
      <button className="primary-button w-full" type="submit" disabled={pending}>
        {pending ? <><span className="spinner border-blue-200 border-t-white" /> Logowanie...</> : "Zaloguj się"}
      </button>
      <p className="text-center text-sm text-slate-600">
        Nie masz konta? <Link className="font-semibold text-blue-700 hover:text-blue-800" href="/register">Utwórz konto</Link>
      </p>
    </form>
  );
}
