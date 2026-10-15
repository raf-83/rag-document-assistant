"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "@/lib/api/client";
import { getDocument } from "./api";
import type { DocumentResponse } from "./types";

const TERMINAL = new Set(["READY", "FAILED"]);

export function useDocumentPolling(documentId: string) {
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(async () => {
    try {
      const result = await getDocument(documentId);
      setDocument(result);
      setError("");

      if (!TERMINAL.has(result.status)) {
        timer.current = setTimeout(load, 3000);
      }
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, [documentId]);

  useEffect(() => {
    void load();
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [load]);

  return { document, loading, error, reload: load };
}
