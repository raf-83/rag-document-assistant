# Naprawa P1: niezależny liveness frontendu

Data: 2026-10-14
Branch: `feature/frontend-kubernetes`

## Problem

Sondy readiness i liveness frontendu Kubernetes korzystały z endpointu `/api/health`.

Endpoint ten działa jako proxy do health endpointu backendu. Gdy backend był niedostępny, frontend zwracał kod 502 również dla liveness probe. Kubernetes interpretował to jako awarię procesu Next.js i po kilku nieudanych próbach restartował poprawnie działający kontener frontendu.

Mogło to powodować:

- niepotrzebne restarty frontendu podczas awarii lub restartu backendu,
- lawinowe restarty i dodatkowe obciążenie klastra,
- utratę rozgrzanego procesu i pamięci podręcznej frontendu,
- wydłużenie czasu powrotu całego systemu do sprawności.

## Wybrane rozwiązanie

Dodano niezależny endpoint:

```text
GET /api/health/live
```

Endpoint zwraca HTTP 200 i tekst `OK`. Nie wykonuje połączeń z backendem, bazą danych ani innymi usługami. Odpowiedź ma nagłówek `Cache-Control: no-store`.

Kubernetes używa teraz:

- `/api/health/live` jako liveness probe — sprawdza wyłącznie, czy proces Next.js obsługuje żądania HTTP,
- `/api/health` jako readiness probe — sprawdza, czy frontend BFF może komunikować się z backendem i jest gotowy do obsługi użytkowników.

Rozdzielenie tych odpowiedzialności jest zgodne z przeznaczeniem sond Kubernetes:

- niepowodzenie liveness oznacza, że kontener należy zrestartować,
- niepowodzenie readiness oznacza, że Pod powinien zostać tymczasowo wyłączony z ruchu, ale nie powinien być restartowany.

## Rozważone alternatywy

### TCP socket probe

Sprawdzałaby wyłącznie, czy port 3000 jest otwarty. Jest prostsza, ale nie potwierdza, że routing i obsługa HTTP Next.js działają poprawnie.

### Zmiana znaczenia istniejącego `/api/health`

Można było zmienić istniejący endpoint na lokalny liveness i dodać osobny endpoint readiness. Odrzucono to rozwiązanie, ponieważ zmieniałoby istniejący kontrakt i zachowanie endpointu używanego do sprawdzania połączenia z backendem.

### Exec probe

Sonda mogłaby sprawdzać proces poleceniem wykonywanym wewnątrz kontenera. Uzależniałoby to jednak manifest od narzędzi dostępnych w obrazie i sprawdzałoby ścieżkę HTTP słabiej niż osobny Route Handler.

## Zmienione pliki

### `frontend/app/api/health/live/route.ts`

Dodano niezależny Route Handler Next.js zwracający HTTP 200 bez zależności zewnętrznych.

### `infra/k8s/frontend-deployment.yaml`

Zmieniono ścieżkę liveness probe:

```yaml
livenessProbe:
  httpGet:
    path: /api/health/live
    port: 3000
```

Readiness probe nadal korzysta z `/api/health`.

## Wykonane testy

- ESLint dla nowego endpointu: **OK**
- TypeScript z `--noEmit`: **OK**
- produkcyjny build Next.js: **OK**
- trasa `/api/health/live` została prawidłowo uwzględniona w buildzie: **OK**
- walidacja manifestów przez `kubectl apply --dry-run=client`: **OK**
- kontrola whitespace: **OK**

Wykonano również test uruchomieniowy z celowo niedostępnym backendem:

| Kontrola | Wynik |
|---|---:|
| `GET /api/health/live` | HTTP 200, `OK` |
| `GET /api/health` | HTTP 502 |

Test potwierdził, że liveness działa niezależnie od backendu, natomiast readiness prawidłowo sygnalizuje brak gotowości całego łańcucha aplikacji.

Proces testowy został zatrzymany po zakończeniu kontroli.

## Zakres operacji

Nie wykonano:

- odczytu sekretów,
- wdrożenia do Kubernetes,
- `git add`,
- commita,
- push,
- merge.
