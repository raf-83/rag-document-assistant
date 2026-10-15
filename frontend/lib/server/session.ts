import "server-only";

import { cookies } from "next/headers";
import type { AuthResponse } from "@/features/auth/types";

export const ACCESS_COOKIE = "adv_access_token";
export const REFRESH_COOKIE = "adv_refresh_token";

const commonOptions = {
  httpOnly: true,
  sameSite: "lax" as const,
  secure:
    process.env.SESSION_COOKIE_SECURE === "true" ||
    (process.env.SESSION_COOKIE_SECURE !== "false" &&
      process.env.NODE_ENV === "production"),
  path: "/",
  priority: "high" as const,
};

export async function setSession(auth: AuthResponse): Promise<void> {
  const store = await cookies();

  store.set(ACCESS_COOKIE, auth.accessToken, {
    ...commonOptions,
    maxAge: auth.expiresIn,
  });
  store.set(REFRESH_COOKIE, auth.refreshToken, commonOptions);
}

export async function clearSession(): Promise<void> {
  const store = await cookies();
  store.set(ACCESS_COOKIE, "", { ...commonOptions, maxAge: 0 });
  store.set(REFRESH_COOKIE, "", { ...commonOptions, maxAge: 0 });
}

export async function getAccessToken(): Promise<string | null> {
  return (await cookies()).get(ACCESS_COOKIE)?.value ?? null;
}

export async function getRefreshToken(): Promise<string | null> {
  return (await cookies()).get(REFRESH_COOKIE)?.value ?? null;
}

export async function hasSession(): Promise<boolean> {
  const store = await cookies();
  return store.has(ACCESS_COOKIE) || store.has(REFRESH_COOKIE);
}
