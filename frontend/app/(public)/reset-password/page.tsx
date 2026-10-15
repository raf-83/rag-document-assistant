import { AuthShell } from "@/components/auth-shell";
import { ResetPasswordForm } from "@/features/auth/reset-password-form";

export default async function ResetPasswordPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const token = (await searchParams).token ?? "";
  return <AuthShell eyebrow="Nowe hasło" title="Ustaw nowe hasło" description="Wybierz bezpieczne hasło składające się z co najmniej 10 znaków."><ResetPasswordForm token={token} /></AuthShell>;
}
