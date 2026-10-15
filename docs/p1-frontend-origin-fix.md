# Naprawa P1: przenośna walidacja Origin frontendu

Data: 2026-10-14
Branch: `feature/frontend-kubernetes`

## Problem

Frontend porównywał nagłówek `Origin` z jedną wartością `FRONTEND_PUBLIC_ORIGIN`. W konfiguracji Kubernetes była ona ustawiona na `http://localhost:13000`.

Powodowało to odrzucanie poprawnych żądań, gdy aplikacja działała:

- przez port-forward na innym porcie,
- pod inną domeną,
- za Ingressem HTTPS,
- w innym środowisku Kubernetes.

Problem dotyczył operacji zmieniających stan, takich jak logowanie, odświeżanie sesji, wylogowanie, rejestracja i wywołania BFF używające metod innych niż GET.

## Wybrane rozwiązanie

Usunięto zależność od statycznego `FRONTEND_PUBLIC_ORIGIN`. Walidacja działa teraz podobnie do ochrony CSRF używanej przez Next.js dla Server Actions:

- lokalnie porównuje `Origin` z nagłówkiem `Host`,
- za reverse proxy korzysta z `X-Forwarded-Host` i `X-Forwarded-Proto`,
- porównuje pełny origin: protokół, host i port,
- odrzuca brakujący, niepoprawny, wielokrotny lub niedopasowany Origin.

Dzięki temu:

- `kubectl port-forward service/frontend 13000:3000` działa bez dodatkowej konfiguracji,
- działa również port-forward na dowolnym innym lokalnym porcie,
- przyszły adres HTTPS, na przykład `https://app.example.com`, zadziała za poprawnie skonfigurowanym Ingressem,
- żądania z obcych źródeł nadal otrzymują HTTP 403.

Walidacja działa w trybie „fail closed”. Jeżeli nie można jednoznacznie ustalić i porównać źródła żądania, operacja jest odrzucana.

## Wymagania dla Ingressu HTTPS

Ingress lub reverse proxy musi:

- ustawiać publiczny host w `X-Forwarded-Host`,
- ustawiać publiczny protokół w `X-Forwarded-Proto`,
- usuwać lub nadpisywać wartości tych nagłówków dostarczone przez klienta.

Dla wdrożenia HTTPS należy również ustawić:

```text
SESSION_COOKIE_SECURE=true
```

Zapobiega to przesyłaniu cookies sesyjnych nieszyfrowanym połączeniem HTTP.

## Rozważone alternatywy

### Konfigurowalna lista dozwolonych Origin

Można było wprowadzić zmienną w rodzaju `FRONTEND_ALLOWED_ORIGINS` zawierającą listę adresów.

Rozwiązanie byłoby bezpieczne, ale wymagałoby aktualizacji konfiguracji dla każdego hosta i portu. Utrudniałoby również port-forwarding. Może być potrzebne w przyszłości, jeżeli UI i BFF będą działały pod różnymi originami.

### Porównanie tylko z `request.url`

Byłoby prostsze, ale za Ingressem wewnętrzny adres i protokół mogą różnić się od publicznego adresu widocznego w przeglądarce. Poprawne żądania HTTPS mogłyby być wtedy odrzucane.

### Wyłączenie walidacji Origin

Ta opcja została odrzucona, ponieważ osłabiłaby ochronę przed CSRF.

## Kompromisy

- Rozwiązanie ufa nagłówkom przekazanym przez Ingress, dlatego proxy musi je sanitizować.
- Brak nagłówka `Origin` jest odrzucany. Jest to celowe dla przeglądarkowej warstwy BFF, ale klient niebędący przeglądarką musi przesłać poprawny Origin albo komunikować się bezpośrednio z backendem.
- Protokół, host i port muszą zgadzać się dokładnie. Zapobiega to zaakceptowaniu żądania pochodzącego z podobnej, ale innej lokalizacji.

## Zmienione pliki

### `frontend/lib/server/request.ts`

Dodano dynamiczne ustalanie oczekiwanego origin na podstawie `Host` albo zaufanych nagłówków reverse proxy oraz bezpieczne odrzucanie niepoprawnych danych.

### `infra/k8s/frontend-configmap.yaml`

Usunięto statyczne:

```text
FRONTEND_PUBLIC_ORIGIN=http://localhost:13000
```

### `compose.yaml`

Usunięto przestarzałe ustawienie `FRONTEND_PUBLIC_ORIGIN` dla frontendu.

### `frontend/README.md`

Dodano opis walidacji Origin, lokalnego port-forwardingu i wymagań dla przyszłego Ingressu HTTPS.

## Wykonane testy

- ESLint: **OK**
- TypeScript z `--noEmit`: **OK**
- produkcyjny build Next.js: **OK**
- walidacja manifestów przez `kubectl apply --dry-run=client`: **OK**
- brak aktywnych odwołań do `FRONTEND_PUBLIC_ORIGIN`: **OK**
- kontrola whitespace: **OK**

### Test uruchomieniowy

Backend był celowo niedostępny, aby odróżnić zaakceptowanie walidacji Origin od odrzucenia żądania. Kod 502 oznacza, że żądanie przeszło walidację i zostało zatrzymane dopiero podczas próby połączenia z backendem.

| Przypadek | Wynik |
|---|---:|
| Zgodny lokalny Origin | zaakceptowany, następnie HTTP 502 |
| Obcy lokalny Origin | HTTP 403 |
| Brak Origin | HTTP 403 |
| Zgodny Origin za HTTPS Ingress | zaakceptowany, następnie HTTP 502 |
| Obcy Origin za HTTPS Ingress | HTTP 403 |
| Niezgodny protokół HTTP/HTTPS | HTTP 403 |

Proces testowy został zatrzymany po zakończeniu kontroli.

## Zakres operacji

Nie wykonano:

- odczytu sekretów,
- wdrożenia do Kubernetes,
- `git add`,
- commita,
- push,
- merge.
