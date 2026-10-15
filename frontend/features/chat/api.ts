import { jsonRequest, requestJson } from "@/lib/api/client";
import type { DocumentChatResponse } from "./types";

export function askDocument(documentId: string, question: string) {
  return requestJson<DocumentChatResponse>(
    `/api/backend/documents/${documentId}/chat`,
    jsonRequest({ question }),
  );
}
