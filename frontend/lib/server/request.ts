import "server-only";

function getSingleHeaderValue(request: Request, name: string): string | null {
  const value = request.headers.get(name)?.trim();

  if (!value || value.includes(",")) {
    return null;
  }

  return value;
}

function getRequestOrigin(request: Request): string | null {
  const requestUrl = new URL(request.url);
  const host =
    getSingleHeaderValue(request, "x-forwarded-host") ??
    getSingleHeaderValue(request, "host") ??
    requestUrl.host;
  const forwardedProtocol = getSingleHeaderValue(request, "x-forwarded-proto");
  const protocol = forwardedProtocol ?? requestUrl.protocol.slice(0, -1);

  if (protocol !== "http" && protocol !== "https") {
    return null;
  }

  try {
    return new URL(`${protocol}://${host}`).origin;
  } catch {
    return null;
  }
}

export function isSameOrigin(request: Request): boolean {
  const origin = getSingleHeaderValue(request, "origin");

  if (!origin) {
    return false;
  }

  try {
    return new URL(origin).origin === origin && origin === getRequestOrigin(request);
  } catch {
    return false;
  }
}

export function invalidOriginResponse(): Response {
  return Response.json(
    { status: 403, message: "Żądanie pochodzi z niedozwolonego źródła." },
    { status: 403 },
  );
}
