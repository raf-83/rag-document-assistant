"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { errorMessage } from "@/lib/api/client";
import { verifyEmail } from "./api";
import { FormMessage } from "./form-message";

export function VerifyEmailState({ token }: { token: string }) {
  const [state, setState] = useState<{ pending: boolean; message: string; error: string }>({ pending: Boolean(token), message: "", error: token ? "" : "Link weryfikacyjny nie zawiera tokenu." });

  useEffect(() => {
    if (!token) return;
    let active = true;
    verifyEmail(token)
      .then((response) => active && setState({ pending: false, message: response.message, error: "" }))
      .catch((reason) => active && setState({ pending: false, message: "", error: errorMessage(reason) }));
    return () => { active = false; };
  }, [token]);

  if (state.pending) return <FormMessage kind="info">Trwa potwierdzanie adresu e-mail...</FormMessage>;

  return (
    <div className="space-y-5">
      {state.message && <FormMessage kind="success">{state.message}</FormMessage>}
      {state.error && <FormMessage kind="error">{state.error}</FormMessage>}
      <Link className="primary-button w-full" href="/login">Przejdź do logowania</Link>
      {state.error && <Link className="secondary-button w-full" href="/resend-verification">Wyślij nowy link</Link>}
    </div>
  );
}
