import { backendFetch } from "@/lib/server/backend";
import { invalidOriginResponse, isSameOrigin } from "@/lib/server/request";
import { clearSession, getRefreshToken } from "@/lib/server/session";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return invalidOriginResponse();
  const refreshToken = await getRefreshToken();

  try {
    if (refreshToken) {
      await backendFetch("/api/auth/logout", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
    }
  } catch {
    // Local session cleanup must succeed even when the backend is unavailable.
  } finally {
    await clearSession();
  }

  return Response.json({ message: "Wylogowano pomyślnie." });
}
