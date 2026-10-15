"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api/client";
import { resetPassword } from "./api";
import { FormMessage } from "./form-message";

export function ResetPasswordForm({ token }: { token: string }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    setError("");
    setPending(true);
    const data = new FormData(form);
    const password = String(data.get("password") ?? "");

    if (password !== String(data.get("confirmation") ?? "")) {
      setError("Hasła muszą być takie same.");
      setPending(false);
      return;
    }

    try {
      const response = await resetPassword(token, password);
      setMessage(response.message);
      form.reset();
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setPending(false);
    }
  }

  if (!token) return <FormMessage kind="error">Link do resetu hasła nie zawiera tokenu.</FormMessage>;

  return (
    <form className="space-y-5" onSubmit={submit}>
      {error && <FormMessage kind="error">{error}</FormMessage>}
      {message && <><FormMessage kind="success">{message}</FormMessage><Link className="primary-button w-full" href="/login">Zaloguj się</Link></>}
      {!message && <>
        <div><label className="field-label" htmlFor="password">Nowe hasło</label><input className="field-input" id="password" name="password" type="password" autoComplete="new-password" minLength={10} maxLength={100} required disabled={pending} /></div>
        <div><label className="field-label" htmlFor="confirmation">Powtórz nowe hasło</label><input className="field-input" id="confirmation" name="confirmation" type="password" autoComplete="new-password" minLength={10} maxLength={100} required disabled={pending} /></div>
        <button className="primary-button w-full" type="submit" disabled={pending}>{pending ? "Zapisywanie..." : "Ustaw nowe hasło"}</button>
      </>}
    </form>
  );
}
