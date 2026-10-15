import type { ApiErrorResponse } from "@/types/api";

export class ApiClientError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly details?: ApiErrorResponse,
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

let refreshPromise: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = fetch("/api/session/refresh", { method: "POST" })
      .then((response) => response.ok)
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

function redirectToLogin(): void {
  if (typeof window === "undefined") return;
  const returnTo = `${window.location.pathname}${window.location.search}`;
  window.location.assign(`/login?returnTo=${encodeURIComponent(returnTo)}`);
}

export async function apiFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
  retryAfterRefresh = true,
): Promise<Response> {
  const response = await fetch(input, init);

  if (response.status !== 401 || !retryAfterRefresh) return response;

  if (await refreshSession()) {
    const retriedResponse = await fetch(input, init);

    if (retriedResponse.status === 401) {
      await fetch("/api/session/logout", { method: "POST" }).catch(() => undefined);
      redirectToLogin();
    }

    return retriedResponse;
  }

  redirectToLogin();
  return response;
}

export async function readApiError(response: Response): Promise<ApiClientError> {
  const fallback = `Żądanie nie powiodło się (HTTP ${response.status}).`;

  try {
    const details = (await response.json()) as Partial<ApiErrorResponse>;
    const message =
      typeof details.message === "string" && details.message.trim()
        ? details.message
        : fallback;

    return new ApiClientError(response.status, message, {
      status: response.status,
      message,
      timestamp: details.timestamp,
      error: details.error,
      path: details.path,
    });
  } catch {
    return new ApiClientError(response.status, fallback);
  }
}

export async function requestJson<T>(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<T> {
  const response = await apiFetch(input, init);
  if (!response.ok) throw await readApiError(response);
  return (await response.json()) as T;
}

export async function publicRequestJson<T>(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(input, init);
  if (!response.ok) throw await readApiError(response);
  return (await response.json()) as T;
}

export function jsonRequest(body: unknown): RequestInit {
  return {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiClientError) return error.message;
  if (error instanceof Error && error.message) return error.message;
  return "Wystąpił nieoczekiwany błąd. Spróbuj ponownie.";
}
