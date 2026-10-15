import type { AuthResponse } from "@/features/auth/types";
import { backendFetch, forwardResponse, unavailableResponse } from "@/lib/server/backend";
import { invalidOriginResponse, isSameOrigin } from "@/lib/server/request";
import { setSession } from "@/lib/server/session";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return invalidOriginResponse();

  try {
    const response = await backendFetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: await request.text(),
    });

    if (!response.ok) return forwardResponse(response);

    const auth = (await response.json()) as AuthResponse;
    await setSession(auth);
    return Response.json({ authenticated: true });
  } catch {
    return unavailableResponse("/api/auth/login");
  }
}
