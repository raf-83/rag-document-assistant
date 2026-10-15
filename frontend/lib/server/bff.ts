import "server-only";

import { backendFetch, forwardResponse, unavailableResponse } from "@/lib/server/backend";
import { invalidOriginResponse, isSameOrigin } from "@/lib/server/request";

export async function forwardPublicJson(
  request: Request,
  backendPath: string,
): Promise<Response> {
  if (!isSameOrigin(request)) return invalidOriginResponse();

  try {
    const response = await backendFetch(backendPath, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: await request.text(),
    });
    return forwardResponse(response);
  } catch {
    return unavailableResponse(backendPath);
  }
}
