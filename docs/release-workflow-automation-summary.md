# Podsumowanie automatyzacji procesu wydania i wdrożenia

## Stan prac

Przygotowano lokalną automatyzację procesu prowadzącego od kontroli zmian do
wydania `v0.1.0` i wdrożenia aplikacji w lokalnym Kubernetes. Żadna operacja
zmieniająca Git, GitHub, GHCR ani klaster Kubernetes nie została wykonana.

GitHub CLI w wersji `2.102.0` został zainstalowany, ale nie jest jeszcze
zalogowany. Jest to aktualna bramka przed dalszą częścią procesu.

## Główne polecenie

Cały proces można uruchomić z katalogu głównego repozytorium:

```powershell
.\scripts\release-workflow.ps1 -Stage All
```

Polecenie jest interaktywne. Automatycznie wykonuje bezpieczne kontrole, ale
zatrzymuje się przed każdą operacją zmieniającą stan i wymaga dokładnego
potwierdzenia.

Proces można również wznawiać etapami:

```powershell
.\scripts\release-workflow.ps1 -Stage Validate
.\scripts\release-workflow.ps1 -Stage Submit
.\scripts\release-workflow.ps1 -Stage Release
.\scripts\release-workflow.ps1 -Stage Deploy -KubeContext docker-desktop
.\scripts\release-workflow.ps1 -Stage Verify -KubeContext docker-desktop
```

## Podział odpowiedzialności

### PowerShell

Skrypt `scripts/release-workflow.ps1`:

- sprawdza wymagane narzędzia, branch oraz stan zmian;
- kontroluje formatowanie diffu i typowe formaty sekretów bez wypisywania ich
  wartości;
- uruchamia testy backendu i kontrole frontendu;
- waliduje Docker Compose, manifesty Kubernetes i GitHub Actions;
- po zatwierdzeniu tworzy commit, wykonuje push i zakłada Pull Request;
- obserwuje CI i po kolejnej zgodzie wykonuje squash merge;
- po zatwierdzeniu tworzy wydanie `v0.1.0`, które uruchamia publikację obrazów;
- sprawdza publiczną dostępność obrazów i ich attestations;
- po zatwierdzeniu synchronizuje sekrety, wdraża aplikację i sprawdza jej stan.

Skrypty sekretów tworzą manifesty w pamięci i przekazują je bezpośrednio do
`kubectl`. Nie wypisują wartości sekretów. Backend i PostgreSQL korzystają z
jednego lokalnego, ignorowanego przez Git pliku `backend-secret.env`, dzięki
czemu hasło bazy nie może się przypadkowo różnić pomiędzy dwoma Secretami.

### GitHub Actions

- `backend-ci.yml` uruchamia testy backendu dla Pull Requesta i brancha `main`.
- `container-images.yml` buduje obrazy backendu i frontendu dla Pull Requesta,
  ale ich nie publikuje.
- Push do `main` publikuje tagi `main` oraz tag oparty na pełnym SHA commita.
- Tag `v0.1.0` publikuje obrazy `0.1.0`, `0.1`, `latest` oraz tag SHA dla
  `linux/amd64` i `linux/arm64`.
- Dla publicznego repozytorium workflow tworzy attestations pochodzenia obrazów.

### Codex CLI

Codex może wykonywać kolejne etapy, analizować wyniki i zatrzymywać się na
bramkach akceptacji. Powtarzalna logika pozostaje jednak w skryptach oraz GitHub
Actions, więc cały proces może zostać wykonany również bez sesji Codex.

## Bramki akceptacji

Każda bramka używa pytania `Continue? (y/n)`. Wyłącznie `y` lub `Y` zatwierdza
operację; Enter, `n`, `N` i każda inna odpowiedź ją anuluje. Przed pytaniem
skrypt pokazuje dokładną operację oraz repozytorium i branch albo kontekst
Kubernetes, namespace i nazwę Secretu. Wartości Secretów nie są wyświetlane.

## Zmienione lub dodane pliki automatyzacji

- `scripts/release-workflow.ps1` — główny orkiestrator procesu wydania;
- `infra/k8s/scripts/sync-postgres-secret.ps1` — bezpieczna synchronizacja
  `postgres-secret`;
- `infra/k8s/scripts/sync-backend-secret.ps1` — anulowanie synchronizacji kończy
  teraz etap błędem i zapobiega przypadkowemu dalszemu wdrożeniu;
- `docs/release-workflow.md` — pełna instrukcja procesu wydania i wdrożenia;
- `infra/k8s/SECRETS.md` — opis jednego lokalnego źródła sekretów;
- `infra/k8s/README.md` — instrukcja automatycznego wydania i wdrożenia;
- `README.md` — odsyłacz do procesu wydania i wdrożenia.

## Wykonane testy

- parser Windows PowerShell dla wszystkich trzech skryptów: poprawny;
- testy backendu Maven: `180/180`, bez błędów;
- frontend `npm ci`: poprawny w izolowanym katalogu;
- frontend ESLint: poprawny;
- frontend TypeScript `tsc --noEmit`: poprawny;
- frontend Next.js production build: poprawny;
- `npm audit --omit=dev`: `0 vulnerabilities` dla zależności produkcyjnych;
- `docker compose config --quiet`: poprawny, z niesekretnymi wartościami
  zastępczymi w osobnym procesie;
- `kubectl kustomize infra/k8s`: poprawny;
- `actionlint` dla obu workflow: poprawny;
- `git diff --check` i kontrola białych znaków nowych plików: poprawne;
- kontrola typowych formatów sekretów: brak trafień, wartości nie były
  wyświetlane;
- test etapu `Submit`: poprawnie wykrywa brak logowania GitHub CLI i kończy się
  przed jakąkolwiek zmianą stanu.

Ostrzeżenie `npm` o pięciu podatnościach dotyczy zależności deweloperskich.
Zależności trafiające do aplikacji produkcyjnej nie mają obecnie zgłoszonych
podatności w wyniku `npm audit --omit=dev`.

## Jednorazowe czynności przed pierwszym wydaniem

1. Zalogować GitHub CLI poleceniem `gh auth login`.
2. Przed utworzeniem `v0.1.0` ustawić repozytorium jako publiczne, aby workflow
   mógł utworzyć attestations.
3. Po pierwszej publikacji ustawić oba pakiety GHCR jako publiczne. Skrypt
   potwierdzi to anonimowym odczytem manifestów i nie przejdzie do wdrożenia,
   dopóki obrazy nie będą dostępne bez tokenu.
4. Przygotować ignorowany plik `infra/k8s/backend-secret.env` zgodnie z
   `infra/k8s/SECRETS.md`.

## Aktualna bramka

Nie wykonano `git add`, commita, push, Pull Requesta, merge, wydania, publikacji
obrazów ani wdrożenia. Następnym krokiem jest interaktywne logowanie GitHub CLI,
które wymaga osobnej zgody użytkownika. Po zalogowaniu proces ponownie zatrzyma
się przed utworzeniem commita.
