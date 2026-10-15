"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api/client";
import { register } from "./api";
import { FormMessage } from "./form-message";

export function RegisterForm() {
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    setError("");
    setPending(true);
    const data = new FormData(form);
    const password = String(data.get("password") ?? "");
    const confirmation = String(data.get("confirmation") ?? "");

    if (password !== confirmation) {
      setError("Hasła muszą być takie same.");
      setPending(false);
      return;
    }

    try {
      const response = await register({
        email: String(data.get("email") ?? "").trim(),
        firstName: String(data.get("firstName") ?? "").trim(),
        lastName: String(data.get("lastName") ?? "").trim(),
        password,
      });
      setMessage(response.message);
      form.reset();
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setPending(false);
    }
  }

  if (message) {
    return (
      <div className="space-y-5">
        <FormMessage kind="success">{message}</FormMessage>
        <Link className="primary-button w-full" href="/login">Przejdź do logowania</Link>
        <Link className="secondary-button w-full" href="/resend-verification">Wyślij wiadomość ponownie</Link>
      </div>
    );
  }

  return (
    <form className="space-y-5" onSubmit={submit}>
      {error && <FormMessage kind="error">{error}</FormMessage>}
      <div className="grid gap-4 sm:grid-cols-2">
        <div><label className="field-label" htmlFor="firstName">Imię</label><input className="field-input" id="firstName" name="firstName" maxLength={100} required disabled={pending} /></div>
        <div><label className="field-label" htmlFor="lastName">Nazwisko</label><input className="field-input" id="lastName" name="lastName" maxLength={100} required disabled={pending} /></div>
      </div>
      <div><label className="field-label" htmlFor="email">Adres e-mail</label><input className="field-input" id="email" name="email" type="email" autoComplete="email" maxLength={320} required disabled={pending} /></div>
      <div><label className="field-label" htmlFor="password">Hasło</label><input className="field-input" id="password" name="password" type="password" autoComplete="new-password" minLength={10} maxLength={100} required disabled={pending} /><p className="mt-1.5 text-xs text-slate-500">Co najmniej 10 znaków.</p></div>
      <div><label className="field-label" htmlFor="confirmation">Powtórz hasło</label><input className="field-input" id="confirmation" name="confirmation" type="password" autoComplete="new-password" minLength={10} maxLength={100} required disabled={pending} /></div>
      <button className="primary-button w-full" type="submit" disabled={pending}>{pending ? "Tworzenie konta..." : "Utwórz konto"}</button>
      <p className="text-center text-sm text-slate-600">Masz już konto? <Link className="font-semibold text-blue-700" href="/login">Zaloguj się</Link></p>
    </form>
  );
}
