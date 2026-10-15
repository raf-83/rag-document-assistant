import { AuthShell } from "@/components/auth-shell";
import { EmailActionForm } from "@/features/auth/email-action-form";

export default function ForgotPasswordPage() {
  return <AuthShell eyebrow="Odzyskiwanie dostępu" title="Zresetuj hasło" description="Podaj adres e-mail powiązany z kontem."><EmailActionForm mode="forgot" /></AuthShell>;
}
