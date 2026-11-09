

\# ADR-001: Worker przetwarzania dokumentów i RabbitMQ



\- Status: propozycja do review

\- Jira: ADV-10

\- Audyt repozytorium: commit `3e5049f`



\## Kontekst



Backend obsługuje API dokumentów, upload, wyszukiwanie i chat. `DocumentService.uploadDocument`

zapisuje plik oraz dokument. Po commicie `DocumentUploadedEventListener` uruchamia

przetwarzanie asynchronicznie przez lokalne zdarzenie Springa. Ekstrakcja, zapis treści,

chunking i embeddings również korzystają z eventów Springa. Repozytorium nie ma jeszcze

RabbitMQ ani osobnego procesu workera.



Obecny status dokumentu to `UPLOADED`, `TEXT\_PROCESSING`, `CONTENT\_READY`, `CHUNKING`,

`READY` lub `FAILED`. `READY` jest ustawiany po zapisaniu chunków, przed zakończeniem

generowania embeddings. Status embeddings jest przechowywany osobno.



Pliki są przechowywane lokalnie przez `StorageService`. Compose montuje katalog hosta.

Kubernetes montuje PVC `ReadWriteOnce` wyłącznie do backendu.



`backend/pom.xml` jest pojedynczym modułem Maven. Obecne workflow budują backend i frontend.

Compose oraz manifest PostgreSQL wskazują obraz `pgvector/pgvector:0.8.6-pg18`; to różni się

od PostgreSQL 16 podanego w kontekście projektu. Ten ADR nie zmienia wersji bazy.



W audytowanych klasach `PdfTextExtractor` odczytuje tekst PDF przez PDFBox, a

`ImageOcrTextExtractor` obsługuje JPEG i PNG. Nie znaleźliśmy tam OCR jako fallbacku dla

skanowanych PDF-ów.



\## Decyzja



\### Backend, worker i współdzielony kod



Backend nadal obsługuje API, JWT, upload, status, search i chat. Worker będzie osobnym

procesem Spring Boot wykonującym ekstrakcję/OCR, chunking i embeddings.



Docelowo repozytorium będzie wielomodułowym projektem Maven z aplikacją backendu, aplikacją

workera i współdzielonym modułem zawierającym potrzebną logikę. Oba procesy będą korzystać

z tej samej implementacji, bez kopiowania kodu. Zakres przenoszonych pakietów ustalimy

w ticketach implementacyjnych po prześledzeniu zależności JPA, storage i security.



Wielomodułowy Maven pozwala osobno budować i wdrażać backend oraz workera, a współdzieloną

logikę wersjonować razem. Osobne repozytorium wymagałoby dodatkowego publikowania

i wersjonowania współdzielonej biblioteki.



\### Wiadomość



Backend zapisze dokument, zlecenie i wpis outbox w jednej transakcji PostgreSQL.

Publisher odczyta outbox i wyśle do RabbitMQ trwałą wiadomość z publisher confirm.



Przykładowy payload:



```json

{

&#x20; "eventType": "document.processing.requested",

&#x20; "schemaVersion": 1,

&#x20; "jobId": "<uuid>",

&#x20; "documentId": "<uuid>",

&#x20; "correlationId": "<uuid>"

}

