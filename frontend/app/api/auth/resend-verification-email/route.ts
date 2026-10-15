import { forwardPublicJson } from "@/lib/server/bff";

export async function POST(request: Request) {
  return forwardPublicJson(request, "/api/auth/resend-verification-email");
}
