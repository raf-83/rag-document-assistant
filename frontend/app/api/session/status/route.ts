import { hasSession } from "@/lib/server/session";

export async function GET() {
  return Response.json({ authenticated: await hasSession() });
}
