import { AuthShell } from "@/components/auth-shell";
import { EmailActionForm } from "@/features/auth/email-action-form";

export default function ResendVerificationPage() {
  return <AuthShell eyebrow="Weryfikacja konta" title="Wyślij link ponownie" description="Podaj adres e-mail użyty podczas rejestracji."><EmailActionForm mode="resend" /></AuthShell>;
}
