import { backendFetch, forwardResponse, unavailableResponse } from "@/lib/server/backend";

export async function GET() {
  try {
    return forwardResponse(await backendFetch("/api/health"));
  } catch {
    return unavailableResponse("/api/health");
  }
}
