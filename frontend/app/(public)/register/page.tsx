import { AuthShell } from "@/components/auth-shell";
import { RegisterForm } from "@/features/auth/register-form";

export default function RegisterPage() {
  return <AuthShell eyebrow="Nowe konto" title="Utwórz swój vault" description="Załóż konto, a następnie potwierdź adres e-mail."><RegisterForm /></AuthShell>;
}
