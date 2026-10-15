# Niezależny przegląd workflow obrazów kontenerowych

Data przeglądu: 2026-10-14

Zakres: `.github/workflows/container-images.yml`, obrazy wskazane w manifestach
Kubernetes, publikowanie do GHCR oraz attestations.

## Wynik

Po wprowadzeniu opisanych niżej poprawek workflow jest gotowy do pierwszego
uruchomienia. Przegląd wykrył dwa konkretne problemy, które mogły powodować błędy
lub mylący wynik pierwszego release.

## Znalezione i naprawione problemy

### 1. Akcje korzystające ze starego runtime Node.js

Trzy przypięte SHA akcji Docker wskazywały wydania deklarujące `node16`:

- `docker/login-action`;
- `docker/metadata-action`;
- `docker/build-push-action`.

GitHub usunął Node 16 z runnerów, a od 23 września 2026 r. akcje JavaScript
działają na Node 24. Stare wersje stwarzały ryzyko, że workflow nie uruchomi się
poprawnie już przy pierwszej próbie.

Wszystkie akcje Docker zostały zaktualizowane do oficjalnych wydań opartych na
Node 24 i nadal są przypięte pełnym, 40-znakowym SHA:

- `docker/setup-qemu-action` — v4.4.0;
- `docker/setup-buildx-action` — v4.4.1;
- `docker/login-action` — v4.6.0;
- `docker/metadata-action` — v6.2.0;
- `docker/build-push-action` — v7.4.0.

Źródło: [GitHub — Node 20 is no longer available in GitHub Actions](https://github.blog/changelog/2026-09-23-node-20-is-no-longer-available-in-github-actions/).

### 2. Attestation niezgodna z typem i widocznością repozytorium

Repozytorium należy do konta osobistego (`raf-83`), a nie organizacji. Workflow
próbował domyślnie tworzyć Artifact Metadata Storage Record, który jest obsługiwany
wyłącznie dla repozytoriów należących do organizacji.

Repozytorium nie jest też obecnie publicznie dostępne. GitHub Artifact Attestations
dla prywatnych repozytoriów wymagają GitHub Enterprise Cloud. W aktualnym stanie
obrazy mogły zostać wypchnięte, ale końcowy krok attestation mógł oznaczyć job jako
nieudany.

Wprowadzona poprawka:

- usunięto uprawnienie `artifact-metadata: write`;
- ustawiono `create-storage-record: false`;
- attestation jest wykonywana automatycznie dla repozytorium publicznego;
- dla repozytorium prywatnego krok jest jawnie pomijany i zapisuje ostrzeżenie.

Jeżeli pierwsze wydanie `v0.1.0` ma otrzymać attestation, repozytorium należy
upublicznić przed utworzeniem tagu wydaniowego.

Źródła:

- [GitHub — dostępność funkcji bezpieczeństwa](https://docs.github.com/en/code-security/getting-started/github-security-features?apiVersion=2022-11-28);
- [actions/attest — konfiguracja obrazu OCI](https://github.com/actions/attest).

## Wyzwalacze i filtry

| Zdarzenie | Zachowanie |
| --- | --- |
| Pull Request do `main` zmieniający backend, frontend lub workflow | Budowa kontrolna bez publikacji |
| Ręczne `workflow_dispatch` | Budowa kontrolna bez publikacji |
| Push do `main` zmieniający backend, frontend lub workflow | Publikacja tagów `main` oraz SHA |
| Push tagu `v0.1.0` | Publikacja wydania |
| Zmiana wyłącznie dokumentacji lub `infra/k8s` | Workflow jest pomijany |
| Tag prerelease, np. `v0.1.0-rc.1` | Workflow jest pomijany celowo |

Wzorzec `v[0-9]+.[0-9]+.[0-9]+` jest poprawnym globem GitHub. Filtry `paths`
nie są sprawdzane dla pushy tagów, dlatego tag `v0.1.0` uruchomi workflow nawet
wtedy, gdy lista zmienionych plików nie pasuje do filtrów.

Źródło: [GitHub — Workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).

Ważna konsekwencja: tag musi wskazywać commit zawierający ten workflow. Jeżeli
`v0.1.0` zostanie utworzony na starszym commicie bez pliku workflow, publikacja się
nie uruchomi.

Jeżeli kontrola `Container images` zostanie kiedyś ustawiona jako obowiązkowa w
branch protection, filtry ścieżek wymagają ponownej oceny. GitHub może pozostawić
pominiętą kontrolę w stanie oczekującym dla Pull Requesta, który nie zmienia
backendu, frontendu ani workflow.

## Tagowanie obrazów

Dla pushu do `main` powstaną tagi:

- `main`;
- `sha-<pełny SHA commita>`.

Dla tagu Git `v0.1.0` powstaną dla obu obrazów:

- `0.1.0`;
- `0.1`;
- `latest`;
- `sha-<pełny SHA commita>`.

Tag `latest` nie jest używany przez Kubernetes. Stabilne manifesty wskazują pełną
wersję semantyczną, a tag SHA pozwala jednoznacznie powiązać obraz z kodem.

Konfiguracja `docker/metadata-action` jest zgodna z dokumentacją akcji:

- `type=semver,pattern={{version}}` usuwa prefiks `v` i daje `0.1.0`;
- `type=semver,pattern={{major}}.{{minor}}` daje `0.1`;
- jawny tag `latest` jest włączony tylko dla tagów wydaniowych;
- `type=sha,format=long` generuje pełny SHA.

## Uprawnienia i publikowanie do GHCR

Globalne uprawnienie workflow to tylko:

```yaml
permissions:
  contents: read
```

Job publikujący otrzymuje dodatkowo:

```yaml
permissions:
  contents: read
  packages: write
  id-token: write
  attestations: write
```

Ocena:

- PR nie ma prawa publikowania pakietów;
- job publikujący uruchamia się tylko dla zdarzenia `push` pasującego do filtrów;
- logowanie do GHCR używa krótkotrwałego `GITHUB_TOKEN`, bez PAT;
- `packages: write` jest wymagane do utworzenia i aktualizacji pakietów GHCR;
- OIDC i `attestations: write` służą wyłącznie do podpisania provenance;
- nazwa obrazu jest normalizowana do małych liter;
- etykieta `org.opencontainers.image.source` łączy pakiet z repozytorium.

Pierwsza publikacja może się nie udać, jeśli ustawienia repozytorium lub konta
blokują GitHub Actions albo zapis do Packages. Tych ustawień nie można w pełni
zweryfikować lokalnie.

## Kubernetes

Wyrenderowane manifesty wskazują:

```text
ghcr.io/raf-83/rag-document-assistant-backend:0.1.0
ghcr.io/raf-83/rag-document-assistant-frontend:0.1.0
```

Nazwy są zgodne z nazwami generowanymi przez workflow. Oba Deploymenty używają tej
samej wersji aplikacji, a `imagePullPolicy: IfNotPresent` jest odpowiednie przy
założeniu, że tagi wydań są niezmienne.

Anonimowa kontrola GHCR zwróciła obecnie `denied` dla obu tagów `0.1.0`. Oznacza
to, że obrazy nie są jeszcze publicznie dostępne — ponieważ nie zostały opublikowane
albo pakiety pozostają prywatne.

Po pierwszej publikacji oba pakiety GHCR trzeba jawnie ustawić jako publiczne.
Publiczne obrazy GHCR można pobierać bez tokenu. Bez tego nowy komputer otrzyma
`ImagePullBackOff` lub błąd autoryzacji.

Źródło: [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).

## Scenariusze pominięcia lub niepowodzenia

1. Workflow nie znajduje się jeszcze w commicie na `main` — tag nie uruchomi
   publikacji.
2. Tag wskazuje starszy commit bez workflow — publikacja zostanie pominięta.
3. Tag nie pasuje do stabilnego SemVer, np. zawiera `-rc.1` — nie zostanie obsłużony.
4. GitHub Actions lub zapis do Packages są zablokowane w ustawieniach — logowanie
   albo push zakończy się błędem.
5. Jeden element macierzy może się udać, a drugi nie — możliwy jest częściowy
   release, np. dostępny backend i brak frontendu. Wtedy nie należy wdrażać wersji.
6. Attestation jest wykonywana po publikacji. Jej błąd może oznaczyć job jako
   nieudany, mimo że sam obraz jest już w GHCR.
7. Pakiety pozostają prywatne po pierwszym pushu — inny komputer nie pobierze ich
   anonimowo.
8. Multi-platform build może nie przejść dla jednej architektury. Należy sprawdzić
   oba warianty `linux/amd64` oraz `linux/arm64` w wyniku pierwszego workflow.

## Pierwszy release `v0.1.0`

Zalecana kolejność:

1. zapisać workflow i powiązane zmiany w commicie;
2. połączyć commit z `main`;
3. jeżeli `v0.1.0` ma mieć attestation, upublicznić repozytorium;
4. dopiero wtedy utworzyć i wypchnąć tag `v0.1.0`;
5. sprawdzić powodzenie backendu i frontendu w macierzy workflow;
6. sprawdzić obecność czterech oczekiwanych tagów obu obrazów;
7. ustawić oba pakiety GHCR jako publiczne;
8. potwierdzić anonimowe pobranie obu manifestów obrazów;
9. dopiero potem testować wdrożenie Kubernetes na czystym komputerze.

Tag `v0.1.0` nie istnieje obecnie w zdalnym repozytorium.

## Wykonane kontrole

| Kontrola | Wynik |
| --- | --- |
| `actionlint 1.7.12` z oficjalnego obrazu, repozytorium tylko do odczytu | PASS, kod 0 |
| `git diff --check` | PASS |
| Renderowanie `kubectl kustomize infra/k8s` | PASS |
| Zgodność dwóch nazw obrazów Kubernetes z workflow | PASS |
| Pełne SHA we wszystkich 10 wystąpieniach `uses:` | PASS |
| Runtime wszystkich użytych akcji | PASS, Node 24 |
| Istnienie zdalnego tagu `v0.1.0` | Nie istnieje |
| Anonimowa dostępność obrazów `0.1.0` w GHCR | `denied` dla obu obrazów |

Nie wykonano commita, push, publikacji, wdrożenia ani zmian sekretów i ustawień
GitHub.
