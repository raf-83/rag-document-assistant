import { UploadForm } from "@/features/documents/upload-form";

export default function UploadPage() {
  return <div><p className="text-sm font-semibold text-blue-700">Nowy plik</p><h1 className="page-heading mt-1">Dodaj dokument</h1><p className="page-description">Prześlij plik i wybierz kategorię. Przetwarzanie rozpocznie się automatycznie.</p><div className="mt-8"><UploadForm /></div></div>;
}
