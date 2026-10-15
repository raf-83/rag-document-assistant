import { AuthShell } from "@/components/auth-shell";
import { VerifyEmailState } from "@/features/auth/verify-email-state";

export default async function VerifyEmailPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const token = (await searchParams).token ?? "";
  return <AuthShell eyebrow="Weryfikacja konta" title="Potwierdź adres e-mail" description="Sprawdzamy poprawność linku aktywacyjnego."><VerifyEmailState token={token} /></AuthShell>;
}
