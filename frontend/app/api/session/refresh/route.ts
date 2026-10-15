import type { AuthResponse } from "@/features/auth/types";
import { backendFetch, forwardResponse, unavailableResponse } from "@/lib/server/backend";
import { invalidOriginResponse, isSameOrigin } from "@/lib/server/request";
import { clearSession, getRefreshToken, setSession } from "@/lib/server/session";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return invalidOriginResponse();
  const refreshToken = await getRefreshToken();

  if (!refreshToken) {
    await clearSession();
    return Response.json({ status: 401, message: "Sesja wygasła." }, { status: 401 });
  }

  try {
    const response = await backendFetch("/api/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      await clearSession();
      return forwardResponse(response);
    }

    await setSession((await response.json()) as AuthResponse);
    return Response.json({ authenticated: true });
  } catch {
    return unavailableResponse("/api/auth/refresh");
  }
}
