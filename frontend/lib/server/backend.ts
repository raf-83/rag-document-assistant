import "server-only";

import type { ApiErrorResponse } from "@/types/api";

export function getBackendBaseUrl(): string {
  const value = process.env.BACKEND_API_URL?.trim();

  if (!value) {
    throw new Error("BACKEND_API_URL is not configured");
  }

  return value.replace(/\/$/, "");
}

export async function backendFetch(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  return fetch(`${getBackendBaseUrl()}${path}`, {
    ...init,
    cache: "no-store",
  });
}

export function unavailableResponse(path: string): Response {
  const body: ApiErrorResponse = {
    timestamp: new Date().toISOString(),
    status: 502,
    error: "Bad Gateway",
    message: "Nie można połączyć się z usługą backendową.",
    path,
  };

  return Response.json(body, { status: 502 });
}

export async function forwardResponse(response: Response): Promise<Response> {
  const headers = new Headers();

  for (const name of ["content-type", "content-disposition", "content-length"]) {
    const value = response.headers.get(name);
    if (value) headers.set(name, value);
  }

  return new Response(await response.arrayBuffer(), {
    status: response.status,
    headers,
  });
}
