import { AuthShell } from "@/components/auth-shell";
import { LoginForm } from "@/features/auth/login-form";

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ returnTo?: string }> }) {
  const requested = (await searchParams).returnTo;
  const returnTo = requested?.startsWith("/") && !requested.startsWith("//") ? requested : "/dashboard";

  return <AuthShell eyebrow="Witaj ponownie" title="Zaloguj się" description="Uzyskaj dostęp do swojego prywatnego repozytorium dokumentów."><LoginForm returnTo={returnTo} /></AuthShell>;
}
