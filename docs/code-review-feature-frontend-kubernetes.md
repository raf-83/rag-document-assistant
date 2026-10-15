# Code review: `feature/frontend-kubernetes`

Data przeglądu: 2026-10-14
Gałąź docelowa: `main`

## Werdykt

**Changes requested — zmiany nie są jeszcze gotowe do merge do `main`.**

Manifesty są poprawne składniowo, testy przechodzą i nie znaleziono prawdziwych sekretów w Git. Pozostają jednak trzy istotne problemy funkcjonalne oraz kilka problemów bezpieczeństwa i przenośności.

## Ustalenia

### [P1] Liveness frontendu zależy od backendu

Probe frontendu wywołuje `/api/health`, a ten endpoint proxy'uje zapytanie do backendu. Awaria lub restart backendu powoduje odpowiedź 502 i restarty zdrowego procesu frontendu.

Liveness powinien sprawdzać wyłącznie proces Next.js. Zależność od backendu można pozostawić najwyżej w readiness.

Lokalizacje:

- `infra/k8s/frontend-deployment.yaml:33`
- `frontend/app/api/health/route.ts:3`

### [P1] Konfiguracja Origin działa wyłącznie dla `localhost:13000`

Wszystkie chronione operacje POST/PUT/DELETE z innego hosta lub portu będą odrzucane kodem 403. Dotyczy to również port-forwardingu na innym porcie, Ingressu i dostępu z innego komputera.

Jednocześnie frontendowy Service jest typu `ClusterIP`, a dokumentacja nie opisuje port-forwardingu na port 13000.

Lokalizacje:

- `infra/k8s/frontend-configmap.yaml:9`
- `frontend/lib/server/request.ts:10`
- `infra/k8s/frontend-service.yaml:15`

### [P1] Obrazy nie są przenośne na nowy komputer lub klaster

Manifesty używają lokalnych, niekwalifikowanych obrazów `backend:1.2` i `frontend:1.1`. Oba obrazy są dostępne na obecnym komputerze, ale na świeżym środowisku Kubernetes spróbuje pobrać je z domyślnego rejestru i najpewniej zakończy się stanem `ImagePullBackOff`.

Brakuje instrukcji build/tag/load albo adresów rejestru z kontrolowanymi tagami lub digestami.

Lokalizacje:

- `infra/k8s/backend-deployment.yaml:22`
- `infra/k8s/frontend-deployment.yaml:22`

### [P2] Dokumentacja sekretów nie pozwala uruchomić całego stosu od zera

Dokumentacja opisuje tylko `backend-secret`, podczas gdy PostgreSQL wymaga osobnego `postgres-secret`. Skrypt synchronizuje wyłącznie sekret backendu, a hasło przekazane backendowi musi odpowiadać hasłu skonfigurowanemu dla bazy.

Na nowym komputerze baza nie wystartuje bez ręcznego odnalezienia i wykorzystania przykładów z `infra/k8s/examples`.

Należy opisać tworzenie obu sekretów oraz użycie tego samego kontekstu i namespace dla wszystkich zasobów.

Lokalizacje:

- `infra/k8s/SECRETS.md:15`
- `infra/k8s/postgres-deployment.yaml:24`

### [P2] Parser pliku sekretów może niejawnie zmienić wartość

Wywołanie `Trim()` usuwa początkowe i końcowe spacje. Dodatkowo wartości zapisane w typowym stylu dotenv, na przykład w cudzysłowach, zachowają cudzysłowy jako część sekretu.

Może to spowodować trudny do zdiagnozowania błąd uwierzytelniania. Skrypt powinien zachowywać dokładną prawą stronę wpisu albo jawnie odrzucać niedozwolony format i dokumentować jego zasady.

Lokalizacja:

- `infra/k8s/scripts/sync-backend-secret.ps1:40`

### [P2] Produkcyjny proces Next.js otrzymuje cookies bez flagi `Secure`

Manifest ustawia `NODE_ENV=production`, ale jednocześnie wymusza `SESSION_COOKIE_SECURE=false`.

Jest to dopuszczalne wyłącznie dla jawnie lokalnego środowiska HTTP. Dla Ingressu lub TLS powinno istnieć osobne ustawienie albo overlay z wartością `true`.

Lokalizacje:

- `infra/k8s/frontend-configmap.yaml:10`
- `frontend/lib/server/session.ts:9`

### [P3] Brakuje podstawowego hardeningu workloadów

Deploymenty nie deklarują:

- requests i limits zasobów,
- `automountServiceAccountToken: false`,
- Kubernetes `securityContext`.

Obrazy uruchamiają aplikacje jako użytkownicy nie-root, co jest poprawne, ale polityka klastra tego nie wymusza.

## Pozostała ocena

- Nazwy Service, selektory, ConfigMapy, PVC oraz `secretKeyRef` są ze sobą spójne.
- Parametry chunkingu odpowiadają właściwościom Spring Boot.
- Dodanie stack trace do błędu chunkingu jest technicznie poprawne i przechodzi testy.
- Zmiana Java jest niezależna od Kubernetes i najlepiej umieścić ją w osobnym commicie.
- `sync-backend-secret.ps1` poprawnie sprawdza obecność `kubectl`, weryfikuje wymagane klucze i kontekst, wymaga jawnego potwierdzenia, przekazuje manifest przez stdin oraz nie wypisuje wartości sekretów.
- Polecenia restartu w dokumentacji powinny zachowywać ten sam `--context` i `--namespace`, którego użyto podczas synchronizacji.

## Kontrola sekretów

Nie znaleziono prawdziwych sekretów w śledzonym drzewie ani historii Git:

- brak wzorców o wysokim prawdopodobieństwie dla tokenów, kluczy prywatnych i JWT,
- historyczne pliki przykładowe zawierają wyłącznie puste wartości, placeholdery lub referencje,
- `.env`, `infra/k8s/backend-secret.env` i `infra/k8s/postgres-secret.env` są prawidłowo ignorowane i nigdy nie były śledzone,
- podczas kontroli nie wyświetlono wartości sekretów.

Skan był lokalny i heurystyczny. Narzędzia `gitleaks` oraz `trufflehog` nie były zainstalowane.

## Wykonane kontrole

- `kubectl apply --dry-run=client` dla 11 manifestów: **OK**
- walidacja składni PowerShell przez parser AST: **OK**
- frontend ESLint: **OK**
- frontend TypeScript z `--noEmit`: **OK**
- backend: **180 testów, 0 failures, 0 errors, 0 skipped**
- `git diff --check`: brak błędów whitespace; występują ostrzeżenia LF/CRLF związane z `core.autocrlf=true` i brakiem głównego `.gitattributes`
- stan śledzonych zmian Git po kontrolach pozostał bez zmian

Nie wykonano wdrożenia, synchronizacji sekretów, restartu, `git add`, commita, push, utworzenia PR ani merge.

Branch nie zawiera jeszcze commitów względem lokalnego `main`; całość planowanego PR znajduje się obecnie w working tree.

## Proponowany komunikat commita

Jeżeli zmiany mają zostać zapisane w jednym commicie:

```text
feat(k8s): add frontend deployment and backend secret workflow

- add frontend ConfigMap, Deployment and Service
- align backend service naming and runtime configuration
- add explicit backend Secret references and synchronization script
- document local Kubernetes secret management
- include stack traces in chunking failure logs
```

Zalecany podział na dwa commity:

```text
feat(k8s): add frontend deployment and secret workflow
fix(backend): log chunking failures with stack traces
```

## Proponowany opis Pull Requesta

```markdown
## Summary

Adds Kubernetes resources for the Next.js frontend and updates the backend
deployment and configuration for local Kubernetes usage.

## Changes

- add frontend ConfigMap, Deployment and ClusterIP Service
- connect the frontend BFF to the backend Kubernetes Service
- align backend Deployment and Service naming
- configure backend chunking properties
- reference required backend secrets explicitly through `secretKeyRef`
- add a PowerShell script for synchronizing `backend-secret`
- document local secret handling and security requirements
- include exception stack traces in document chunking failure logs

## Security

- real credentials are not stored in the repository
- local secret files are excluded through `.gitignore`
- secret values are passed to `kubectl` through stdin and are not printed
- Kubernetes Secrets remain namespace-scoped and require appropriate RBAC

## Verification

- Kubernetes manifests validated with `kubectl apply --dry-run=client`
- PowerShell script syntax validated
- frontend ESLint passed
- frontend TypeScript check passed
- backend test suite passed: 180 tests
- Git tree and history scanned for likely credentials

## Before merge

- decouple frontend liveness from backend availability
- make the public frontend origin configurable per environment
- document or automate image build/push/load on a fresh machine
- document provisioning of `postgres-secret`
- define production-safe cookie configuration
- clarify exact parsing rules for the backend secret env file
```
