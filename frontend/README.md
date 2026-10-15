# RAG Document Assistant Frontend

Frontend aplikacji RAG Document Assistant zbudowany w Next.js, React i TypeScript. Aplikacja korzysta z Route Handlers jako warstwy BFF między przeglądarką a backendem Spring Boot.

## Wymagania

- Node.js zgodny z Next.js 16,
- działający backend RAG Document Assistant,
- skonfigurowane środowisko backendu i baza PostgreSQL.

## Konfiguracja

Skopiuj przykładowy plik środowiskowy:

```powershell
Copy-Item .env.example .env.local
```

Domyślna konfiguracja developerska:

```text
BACKEND_API_URL=http://localhost:8080
```

`BACKEND_API_URL` jest używany wyłącznie po stronie serwera Next.js. Nie umieszczaj w plikach środowiskowych tokenów użytkowników ani kluczy API backendu.

### Walidacja Origin

Operacje zmieniające stan porównują nagłówek `Origin` z publicznym hostem i protokołem bieżącego żądania. Brakujący, niepoprawny lub niedopasowany nagłówek jest odrzucany kodem 403.

Nie trzeba konfigurować stałego publicznego adresu frontendu:

- lokalne uruchomienie i `kubectl port-forward service/frontend 13000:3000` korzystają z nagłówka `Host`,
- za Ingressem HTTPS aplikacja korzysta z `X-Forwarded-Host` i `X-Forwarded-Proto`.

Ingress lub reverse proxy musi nadpisywać te nagłówki publicznym hostem i protokołem oraz usuwać wartości dostarczone przez klienta. Dla HTTPS ustaw również `SESSION_COOKIE_SECURE=true` w konfiguracji danego środowiska.

## Uruchomienie

```powershell
npm ci
npm run dev
```

Aplikacja będzie dostępna pod adresem `http://localhost:3000`.

## Kontrole jakości

```powershell
npm run lint
npm run typecheck
npm run build
```

## Dostępne obszary

- rejestracja, logowanie i odzyskiwanie konta,
- bezpieczna sesja oparta o HttpOnly cookies,
- lista, upload, szczegóły, treść i pobieranie dokumentów,
- monitoring przetwarzania dokumentów,
- wyszukiwanie pełnotekstowe, semantyczne i hybrydowe,
- chat RAG dla gotowych dokumentów.
