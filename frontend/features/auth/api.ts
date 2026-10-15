import { jsonRequest, publicRequestJson } from "@/lib/api/client";
import type { ApiMessageResponse } from "@/types/api";
import type { LoginRequest, RegisterRequest, SessionResponse } from "./types";

export function login(request: LoginRequest) {
  return publicRequestJson<SessionResponse>("/api/session/login", jsonRequest(request));
}

export function register(request: RegisterRequest) {
  return publicRequestJson<ApiMessageResponse>("/api/auth/register", jsonRequest(request));
}

export function resendVerificationEmail(email: string) {
  return publicRequestJson<ApiMessageResponse>(
    "/api/auth/resend-verification-email",
    jsonRequest({ email }),
  );
}

export function forgotPassword(email: string) {
  return publicRequestJson<ApiMessageResponse>(
    "/api/auth/forgot-password",
    jsonRequest({ email }),
  );
}

export function resetPassword(token: string, newPassword: string) {
  return publicRequestJson<ApiMessageResponse>(
    "/api/auth/reset-password",
    jsonRequest({ token, newPassword }),
  );
}

export function verifyEmail(token: string) {
  return publicRequestJson<ApiMessageResponse>(
    `/api/auth/verify-email?token=${encodeURIComponent(token)}`,
  );
}
