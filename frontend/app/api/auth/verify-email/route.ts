import { backendFetch, forwardResponse, unavailableResponse } from "@/lib/server/backend";

export async function GET(request: Request) {
  const token = new URL(request.url).searchParams.get("token");

  if (!token) {
    return Response.json(
      { status: 400, message: "Brak tokenu weryfikacyjnego." },
      { status: 400 },
    );
  }

  const path = `/api/auth/verify-email?token=${encodeURIComponent(token)}`;

  try {
    return forwardResponse(await backendFetch(path, { headers: { Accept: "application/json" } }));
  } catch {
    return unavailableResponse("/api/auth/verify-email");
  }
}
