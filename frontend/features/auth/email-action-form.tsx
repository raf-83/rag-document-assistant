"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api/client";
import { forgotPassword, resendVerificationEmail } from "./api";
import { FormMessage } from "./form-message";

export function EmailActionForm({ mode }: { mode: "forgot" | "resend" }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    setPending(true);
    const email = String(new FormData(event.currentTarget).get("email") ?? "").trim();

    try {
      const response = mode === "forgot" ? await forgotPassword(email) : await resendVerificationEmail(email);
      setMessage(response.message);
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="space-y-5" onSubmit={submit}>
      {error && <FormMessage kind="error">{error}</FormMessage>}
      {message && <FormMessage kind="success">{message}</FormMessage>}
      <div><label className="field-label" htmlFor="email">Adres e-mail</label><input className="field-input" id="email" name="email" type="email" autoComplete="email" maxLength={320} required disabled={pending} /></div>
      <button className="primary-button w-full" type="submit" disabled={pending}>{pending ? "Wysyłanie..." : mode === "forgot" ? "Wyślij instrukcję" : "Wyślij ponownie"}</button>
      <Link className="secondary-button w-full" href="/login">Wróć do logowania</Link>
    </form>
  );
}
