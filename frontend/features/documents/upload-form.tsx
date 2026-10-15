"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ErrorState, LoadingState } from "@/components/ui/feedback";
import { FormMessage } from "@/features/auth/form-message";
import { errorMessage } from "@/lib/api/client";
import { getDocumentTypes, uploadDocument } from "./api";
import type { DocumentTypeResponse } from "./types";

const ALLOWED_EXTENSIONS = ["pdf", "jpg", "jpeg", "png"];

export function UploadForm() {
  const router = useRouter();
  const [types, setTypes] = useState<DocumentTypeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  async function loadTypes() {
    setLoading(true);
    setLoadError("");
    try { setTypes((await getDocumentTypes()).filter((type) => type.active)); }
    catch (reason) { setLoadError(errorMessage(reason)); }
    finally { setLoading(false); }
  }

  useEffect(() => { void loadTypes(); }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const data = new FormData(event.currentTarget);
    const file = data.get("file");

    if (!(file instanceof File) || file.size === 0) { setError("Wybierz plik do przesłania."); return; }
    const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
    if (!ALLOWED_EXTENSIONS.includes(extension)) { setError("Dozwolone formaty to PDF, JPG, JPEG i PNG."); return; }

    setPending(true);
    try {
      const document = await uploadDocument(file, { title: String(data.get("title") ?? "").trim(), documentTypeId: String(data.get("documentTypeId") ?? "") });
      router.push(`/documents/${document.id}`);
    } catch (reason) {
      setError(errorMessage(reason));
      setPending(false);
    }
  }

  if (loading) return <LoadingState label="Ładowanie typów dokumentów..." />;
  if (loadError) return <ErrorState message={loadError} onRetry={loadTypes} />;

  return (
    <form className="panel max-w-2xl space-y-6 p-5 sm:p-7" onSubmit={submit}>
      {error && <FormMessage kind="error">{error}</FormMessage>}
      <div><label className="field-label" htmlFor="title">Tytuł dokumentu</label><input className="field-input" id="title" name="title" maxLength={255} required disabled={pending} placeholder="Np. Umowa najmu 2026" /></div>
      <div><label className="field-label" htmlFor="documentTypeId">Typ dokumentu</label><select className="field-input" id="documentTypeId" name="documentTypeId" required disabled={pending} defaultValue=""><option value="" disabled>Wybierz typ</option>{types.map((type) => <option value={type.id} key={type.id}>{type.name}</option>)}</select></div>
      <div><label className="field-label" htmlFor="file">Plik</label><label className="flex cursor-pointer flex-col items-center rounded-xl border border-dashed border-slate-300 bg-slate-50 px-6 py-9 text-center transition hover:border-blue-400 hover:bg-blue-50/40"><span className="flex size-11 items-center justify-center rounded-xl bg-white text-xl shadow-sm" aria-hidden="true">↑</span><span className="mt-3 font-semibold text-slate-800">Wybierz plik z urządzenia</span><span className="mt-1 text-sm text-slate-500">PDF, JPG, JPEG lub PNG</span><input className="mt-4 block max-w-full text-sm text-slate-600" id="file" name="file" type="file" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" required disabled={pending} /></label></div>
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end"><Link className="secondary-button" href="/documents">Anuluj</Link><button className="primary-button" type="submit" disabled={pending || types.length === 0}>{pending ? "Przesyłanie..." : "Prześlij dokument"}</button></div>
    </form>
  );
}
