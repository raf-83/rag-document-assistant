import type { DocumentStatus } from "@/features/documents/types";

export type SearchFilters = {
  query: string;
  limit?: number;
  documentTypeId?: string;
  createdFrom?: string;
  createdTo?: string;
  scopeDocumentIds?: string[];
};

export type DocumentSearchResult = {
  documentId: string;
  title: string;
  documentType: string;
  status: DocumentStatus;
  titleSnippet: string | null;
  contentSnippet: string | null;
  matchedInTitle: boolean;
  matchedInContent: boolean;
  rank: number;
  createdAt: string;
  updatedAt: string;
};

export type DocumentSearchPage = {
  results: DocumentSearchResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type SemanticSearchResult = {
  documentId: string;
  documentTitle: string;
  documentTypeId: string;
  documentTypeName: string;
  chunkId: string;
  chunkIndex: number;
  score: number;
  textPreview: string;
  documentCreatedAt: string;
};

export type SemanticSearchResponse = {
  limit: number;
  resultCount: number;
  results: SemanticSearchResult[];
};

export type HybridMatchType = "SEMANTIC_ONLY" | "KEYWORD_ONLY" | "HYBRID";

export type HybridSearchChunk = {
  chunkId: string;
  chunkIndex: number;
  score: number;
  semanticScore: number;
  keywordScore: number;
  matchType: HybridMatchType;
  textPreview: string;
};

export type HybridSearchResult = {
  documentId: string;
  documentTitle: string;
  documentTypeId: string;
  documentTypeName: string;
  score: number;
  bestChunkScore: number;
  matchedChunkCount: number;
  chunks: HybridSearchChunk[];
  documentCreatedAt: string;
};

export type HybridSearchResponse = {
  limit: number;
  resultCount: number;
  results: HybridSearchResult[];
};
