import { apiFetch, readApiError, requestJson } from "@/lib/api/client";
import type {
  DocumentContentResponse,
  DocumentResponse,
  DocumentStatus,
  DocumentTypeResponse,
  UploadDocumentRequest,
} from "./types";

const API = "/api/backend";

export function getDocuments(status?: DocumentStatus) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return requestJson<DocumentResponse[]>(`${API}/documents${query}`);
}

export function getDocument(documentId: string) {
  return requestJson<DocumentResponse>(`${API}/documents/${documentId}`);
}

export function getDocumentTypes() {
  return requestJson<DocumentTypeResponse[]>(`${API}/document-types`);
}

export function getDocumentContent(documentId: string) {
  return requestJson<DocumentContentResponse>(`${API}/documents/${documentId}/content`);
}

export async function uploadDocument(file: File, metadata: UploadDocumentRequest) {
  const body = new FormData();
  body.append("file", file);
  body.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));

  return requestJson<DocumentResponse>(`${API}/documents`, { method: "POST", body });
}

export async function downloadDocument(document: DocumentResponse): Promise<void> {
  const response = await apiFetch(`${API}/documents/${document.id}/file`);
  if (!response.ok) throw await readApiError(response);

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = window.document.createElement("a");
  anchor.href = url;
  anchor.download = document.storedFile.originalFileName;
  window.document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
