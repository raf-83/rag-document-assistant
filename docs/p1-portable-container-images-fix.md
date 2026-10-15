# P1 — przenośne obrazy kontenerowe dla Kubernetes

Data analizy: 2026-10-14

Branch: `feature/frontend-kubernetes`

## Problem

Deploymenty Kubernetes wskazywały obrazy dostępne wyłącznie w lokalnym Dockerze.
Na komputerze autora klastra mogły działać dzięki lokalnemu cache, ale nowy klaster
nie miał miejsca, z którego mógłby je pobrać. Skutkiem był `ImagePullBackOff` albo
konieczność ręcznego budowania i importowania obrazów na każdym komputerze. Taki
proces nie gwarantował też, że dwie osoby uruchamiają dokładnie tę samą wersję.

## Wybrane rozwiązanie

Wybrano GitHub Container Registry (GHCR) oraz osobny workflow GitHub Actions dla
obrazów backendu i frontendu:

- stabilne manifesty wskazują wspólną wersję wydaniową `0.1.0`;
- wydanie Git `v0.1.0` publikuje oba obrazy pod tagami `0.1.0`, `0.1`, `latest`
  oraz `sha-<pełny SHA commita>`;
- zmiana na `main` publikuje tylko znaczniki `main` i SHA;
- Pull Request i ręczne uruchomienie jedynie sprawdzają budowę — niczego nie
  publikują;
- obrazy wydaniowe są wieloplatformowe: `linux/amd64` i `linux/arm64`;
- Kubernetes nie używa ruchomego tagu `latest`;
- `GITHUB_TOKEN` zastępuje długowieczny token przechowywany jako sekret;
- akcje workflow są przypięte do pełnych SHA commitów;
- job publikujący ma minimalne wymagane uprawnienia i w publicznym repozytorium
  generuje poświadczenie pochodzenia obrazu;
- etykieta OCI `org.opencontainers.image.source` łączy pakiet z repozytorium.

GHCR dobrze integruje się z procesem CI/CD, ponieważ kod, pipeline i obrazy
pozostają w jednym serwisie, a publiczne obrazy można pobierać anonimowo. Docker
Compose nadal buduje ze źródeł i pozostaje prostym trybem developerskim oraz
awaryjną alternatywą bez rejestru.

## Wersjonowanie

Numery obrazów oznaczają wersję całej aplikacji, więc backend i frontend dostają
ten sam numer. Tag wersji wydaniowej należy traktować jako niezmienny. Tag SHA daje
pełną identyfikowalność, a środowisko o podwyższonych wymaganiach może wskazać
bezpośrednio digest `sha256` zamiast tagu.

Przy kolejnym wydaniu należy najpierw opublikować oba obrazy z tagu Git, a potem w
osobnym Pull Requeście zmienić obie referencje w Kubernetes na nową pełną wersję.

## Bezpieczeństwo

- Workflow PR ma globalnie tylko `contents: read`.
- Uprawnienia `packages`, OIDC i attestations występują tylko w jobie publikującym
  po zmianie na `main` lub tagu wydaniowym.
- Publikacja korzysta z automatycznego, krótkotrwałego `GITHUB_TOKEN`; rozwiązanie
  nie dodaje nowych sekretów repozytorium.
- Obrazy uruchamiają aplikacje jako użytkownicy bez uprawnień root.
- `.dockerignore` backendu i frontendu wykluczają pliki środowiskowe i typowe pliki
  z sekretami z kontekstu budowy.
- Sekrety Kubernetes nie są elementem `kustomization.yaml` ani obrazów.
- Nowe pakiety GHCR są domyślnie prywatne. Aby umożliwić wdrożenia bez
  uwierzytelniania do rejestru, właściciel powinien po pierwszej publikacji jawnie
  ustawić oba jako publiczne. Wariant prywatny wymaga poświadczeń tylko do odczytu
  w klastrze; tokenu nie wolno zapisywać w repozytorium.
- GitHub Artifact Attestations w prywatnym repozytorium wymagają GitHub Enterprise
  Cloud. Workflow jawnie pomija ten krok dla repozytorium prywatnego; aby wydanie
  `v0.1.0` miało attestation, repozytorium musi być publiczne przed utworzeniem tagu.

Nie przypięto bazowych obrazów Dockerfile do digestów. Są ograniczone wersją
główną/dystrybucją, co upraszcza pobieranie poprawek bezpieczeństwa. Ceną jest brak
bitowej powtarzalności ponownego builda w odległej przyszłości. Powtarzalność
wdrożenia zapewnia opublikowany artefakt wydaniowy; dla bardziej rygorystycznego
łańcucha dostaw warto później dodać kontrolowane aktualizacje digestów bazowych.

## Rozważone alternatywy

| Podejście | Ocena |
| --- | --- |
| GHCR + GitHub Actions | Wybrane: najmniej dodatkowych kont i sekretów, spójne wersjonowanie artefaktów i provenance |
| Docker Hub | Technicznie poprawne, ale wymaga osobnego konta i tokenu CI bez korzyści dla tego repozytorium |
| Lokalny build i import do kind/minikube | Przydatny diagnostycznie, ale nieprzenośny i niepowtarzalny |
| Prywatny rejestr utrzymywany samodzielnie | Nadmierny koszt obsługi, TLS, dostępności i rotacji poświadczeń dla tego modelu wdrożenia |
| Budowanie obrazu podczas wdrożenia | Łączy build z runtime, zwiększa uprawnienia klastra i utrudnia audyt — odrzucone |

## Zmienione pliki

- `.github/workflows/container-images.yml` — walidacja i publikacja dwóch obrazów
  do GHCR, multi-arch, tagowanie oraz provenance.
- `infra/k8s/backend-deployment.yaml` — wersjonowany obraz backendu z GHCR.
- `infra/k8s/frontend-deployment.yaml` — wersjonowany obraz frontendu z GHCR.
- `infra/k8s/kustomization.yaml` — jedna lista wszystkich jawnych zasobów potrzebna
  do `kubectl apply -k`; sekrety są celowo poza listą.
- `infra/k8s/README.md` — instrukcja publikacji, wersjonowania i uruchomienia na
  nowym komputerze, wraz z kompromisami.
- `README.md` — odnośnik do nowej instrukcji Kubernetes.
- `docs/p1-portable-container-images-fix.md` — ten raport.

Dockerfile backendu, Dockerfile frontendu i `compose.yaml` przeanalizowano, ale nie
wymagały zmian dla tego problemu. Oba Dockerfile są wieloetapowe i kończą się
nieuprzywilejowanym użytkownikiem. Compose celowo buduje lokalny kod.

## Testy

| Kontrola | Wynik |
| --- | --- |
| `kubectl kustomize infra/k8s` | PASS — poprawnie wyrenderowano ConfigMapy, usługi, PVC i trzy Deploymenty; sekretów nie wyrenderowano |
| `docker compose config --quiet` z atrapami wymaganych zmiennych | PASS — kod wyjścia 0; żadnego kontenera nie uruchomiono |
| Lokalny build obrazu backendu | PASS — obraz `linux/amd64` utworzony jako użytkownik `app` |
| Lokalny build obrazu frontendu | PASS — pełny `next build`, obraz `linux/amd64` utworzony jako `appuser` |
| Kontrola pełnych SHA akcji | PASS — wszystkie 10 wystąpień `uses:` przypięte do 40-znakowego SHA |
| `git diff --check` i kontrola trailing whitespace nowych plików | PASS |
| Próba builda backendu z `--network=none` | Oczekiwane zatrzymanie na niedostępnych zależnościach Maven/APT; ponowiono za zgodą z dostępem wychodzącym i zakończono powodzeniem |
| `actionlint 1.7.12` uruchomiony z oficjalnego obrazu | PASS — kod wyjścia 0 po aktualizacji akcji do runtime Node 24 |

Nie wykonano publikacji, logowania do GHCR, wieloplatformowego push, wdrożenia ani
zmiany działającego klastra. Lokalnie pozostały wyłącznie dwa obrazy testowe z
tagiem `p1-image-review`.

## Czynności wymagające osobnej zgody

Rozwiązanie zacznie działać na nowym komputerze dopiero po wykonaniu przez
właściciela repozytorium następujących operacji zewnętrznych:

1. scalenie zmian z workflow do `main`;
2. utworzenie i wypchnięcie tagu Git `v0.1.0`;
3. sprawdzenie powodzenia obu buildów i attestations w GitHub Actions;
4. ustawienie pakietów `rag-document-assistant-backend` i
   `rag-document-assistant-frontend` jako publiczne w GHCR;
5. test wdrożenia na czystym klastrze.

Żadna z tych operacji nie została wykonana w ramach naprawy.

## Uruchomienie przez inną osobę — skrót

Po opublikowaniu `0.1.0` nowy użytkownik klonuje repozytorium, przygotowuje dwa
sekrety Kubernetes zgodnie z `infra/k8s/README.md`, a następnie uruchamia:

```powershell
kubectl apply -k infra/k8s -n rag-document-assistant
kubectl port-forward -n rag-document-assistant service/frontend 13000:3000
```

Kubernetes pobierze wersjonowane obrazy z GHCR automatycznie. Nie trzeba budować
backendu ani frontendu na nowym komputerze ani importować obrazów do klastra.
