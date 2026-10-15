export const DOCUMENT_STATUSES = [
  "UPLOADED",
  "TEXT_PROCESSING",
  "CONTENT_READY",
  "CHUNKING",
  "READY",
  "FAILED",
] as const;

export type DocumentStatus = (typeof DOCUMENT_STATUSES)[number];

export type StoredFileResponse = {
  originalFileName: string;
  mimeType: string;
  fileExtension: string;
  sizeInBytes: number;
};

export type DocumentResponse = {
  id: string;
  title: string;
  ownerId: string;
  documentTypeId: string;
  documentTypeCode: string;
  status: DocumentStatus;
  createdAt: string;
  updatedAt: string;
  storedFile: StoredFileResponse;
};

export type DocumentTypeResponse = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
};

export type DocumentContentResponse = {
  documentId: string;
  sanitizedText: string;
  extractionMethod: "PDF_TEXT" | "OCR" | string;
  characterCount: number | null;
  pageCount: number | null;
  extractedAt: string | null;
  sanitizedAt: string | null;
};

export type UploadDocumentRequest = {
  title: string;
  documentTypeId: string;
};
