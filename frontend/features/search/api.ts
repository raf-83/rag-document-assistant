import { jsonRequest, requestJson } from "@/lib/api/client";
import type {
  DocumentSearchPage,
  HybridSearchResponse,
  SearchFilters,
  SemanticSearchResponse,
} from "./types";

const API = "/api/backend";

export function fullTextSearch(query: string, page = 0, size = 20) {
  const params = new URLSearchParams({ query, page: String(page), size: String(size) });
  return requestJson<DocumentSearchPage>(`${API}/documents/search?${params}`);
}

export function semanticSearch(filters: SearchFilters) {
  return requestJson<SemanticSearchResponse>(
    `${API}/search/semantic`,
    jsonRequest(filters),
  );
}

export function hybridSearch(filters: SearchFilters) {
  return requestJson<HybridSearchResponse>(
    `${API}/search/hybrid`,
    jsonRequest(filters),
  );
}
