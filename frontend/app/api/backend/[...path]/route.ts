import { backendFetch, forwardResponse, unavailableResponse } from "@/lib/server/backend";
import { invalidOriginResponse, isSameOrigin } from "@/lib/server/request";
import { getAccessToken } from "@/lib/server/session";

type Context = { params: Promise<{ path: string[] }> };

const UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";

function isAllowed(path: string, method: string): boolean {
  if (method === "GET") {
    return (
      path === "documents" ||
      path === "documents/search" ||
      path === "document-types" ||
      new RegExp(`^document-types/${UUID}$`).test(path) ||
      new RegExp(`^documents/${UUID}$`).test(path) ||
      new RegExp(`^documents/${UUID}/(content|file)$`).test(path)
    );
  }

  if (method === "POST") {
    return (
      path === "documents" ||
      path === "search/semantic" ||
      path === "search/hybrid" ||
      new RegExp(`^documents/${UUID}/chat$`).test(path)
    );
  }

  return false;
}

async function handle(request: Request, context: Context): Promise<Response> {
  const { path: segments } = await context.params;
  const path = segments.join("/");

  if (!isAllowed(path, request.method)) {
    return Response.json({ status: 404, message: "Nieznana operacja API." }, { status: 404 });
  }

  if (request.method !== "GET" && !isSameOrigin(request)) {
    return invalidOriginResponse();
  }

  const accessToken = await getAccessToken();
  if (!accessToken) {
    return Response.json({ status: 401, message: "Sesja wygasła." }, { status: 401 });
  }

  const sourceUrl = new URL(request.url);
  const backendPath = `/api/${path}${sourceUrl.search}`;
  const headers = new Headers({
    Accept: request.headers.get("accept") ?? "application/json",
    Authorization: `Bearer ${accessToken}`,
  });

  let body: BodyInit | undefined;
  if (request.method !== "GET") {
    const contentType = request.headers.get("content-type");
    if (contentType) headers.set("Content-Type", contentType);
    body = await request.arrayBuffer();
  }

  try {
    const response = await backendFetch(backendPath, {
      method: request.method,
      headers,
      body,
    });
    return forwardResponse(response);
  } catch {
    return unavailableResponse(backendPath);
  }
}

export function GET(request: Request, context: Context) {
  return handle(request, context);
}

export function POST(request: Request, context: Context) {
  return handle(request, context);
}
