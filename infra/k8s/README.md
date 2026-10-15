# Kubernetes: obrazy kontenerowe i uruchomienie

Manifesty używają publicznych obrazów wydaniowych z GitHub Container Registry
(GHCR):

- `ghcr.io/raf-83/rag-document-assistant-backend:0.1.0`
- `ghcr.io/raf-83/rag-document-assistant-frontend:0.1.0`

Oba obrazy mają ten sam numer wersji aplikacji. Tag wydaniowy jest traktowany jako
niezmienny: opublikowanej wersji nie należy nadpisywać. Workflow publikuje także
tag `sha-<pełny SHA commita>`, który pozwala wskazać dokładny kod źródłowy obrazu.

## Publikowanie obrazów

Workflow `.github/workflows/container-images.yml`:

- dla Pull Requestów i uruchomień ręcznych tylko buduje obrazy i niczego nie
  publikuje;
- po zmianie na `main` publikuje tagi `main` oraz `sha-<pełny SHA commita>`;
- po wypchnięciu tagu Git w formacie `vMAJOR.MINOR.PATCH`, na przykład `v0.1.0`,
  publikuje `MAJOR.MINOR.PATCH`, `MAJOR.MINOR`, `latest` i tag SHA;
- buduje warianty `linux/amd64` i `linux/arm64`;
- korzysta z krótkotrwałego `GITHUB_TOKEN`, nadaje etykiety OCI i dla publicznego
  repozytorium tworzy poświadczenie pochodzenia obrazu;
- ma akcje zewnętrzne przypięte do pełnych SHA commitów.

Pierwsze wydanie `0.1.0` wymaga osobno zatwierdzonych operacji w GitHub: scalenia
workflow, utworzenia i wypchnięcia tagu `v0.1.0` oraz ustawienia obu pakietów GHCR
jako publiczne. GitHub tworzy nowy pakiet jako prywatny. Tych operacji nie wykonuje
się z poziomu manifestów i nie zostały wykonane w ramach tej zmiany.

Repozytoria prywatne wymagają GitHub Enterprise Cloud do tworzenia GitHub Artifact
Attestations. Workflow pomija attestation z jawnym ostrzeżeniem, gdy repozytorium
nie jest publiczne. Jeśli pierwsze wydanie `v0.1.0` ma otrzymać attestation,
repozytorium należy upublicznić przed utworzeniem tagu wydaniowego.

Publiczne obrazy upraszczają powtarzalne wdrożenia: można je pobrać bez tokenu i
bez tworzenia `imagePullSecret`. Jeśli repozytorium lub obrazy mają pozostać
prywatne, administrator musi zamiast tego skonfigurować w klastrze poświadczenia
tylko do odczytu i dodać `imagePullSecrets` do obu Deploymentów. Nie należy
umieszczać tokenu w repozytorium.

## Uruchomienie na nowym komputerze

Wymagane są Docker oraz klaster Kubernetes z `kubectl` (na przykład Docker
Desktop, Rancher Desktop, kind lub minikube). Manifesty uruchamiają także lokalny
PostgreSQL z rozszerzeniem pgvector.

1. Sklonuj repozytorium i przejdź do jego katalogu.
2. Upewnij się, że wydanie wskazane w manifestach istnieje w GHCR i jest publiczne.
3. Skonfiguruj lokalny, ignorowany plik `backend-secret.env` zgodnie z
   [SECRETS.md](SECRETS.md). Oba skrypty sekretów korzystają z tego samego hasła
   bazy, ale tworzą w Kubernetes osobne zasoby `backend-secret` i
   `postgres-secret`. Nie zapisuj rzeczywistych wartości w plikach śledzonych
   przez Git.
4. Utwórz namespace i oba sekrety, a następnie zastosuj zasoby:

   ```powershell
   kubectl create namespace rag-document-assistant
   ./infra/k8s/scripts/sync-backend-secret.ps1 -Namespace rag-document-assistant
   ./infra/k8s/scripts/sync-postgres-secret.ps1 -Namespace rag-document-assistant
   kubectl apply -k infra/k8s -n rag-document-assistant
   kubectl get pods -n rag-document-assistant
   ```

5. Po uzyskaniu stanu `Ready` udostępnij frontend lokalnie przez port-forward
   wskazany dla usługi frontendu, na przykład:

   ```powershell
   kubectl get services -n rag-document-assistant
   kubectl port-forward -n rag-document-assistant service/frontend 13000:3000
   ```

   Aplikacja będzie dostępna pod `http://localhost:13000`. Walidacja Origin obsługuje
   również przyszły adres HTTPS przekazany przez zaufany reverse proxy lub Ingress.

Polecenie tworzące namespace jest potrzebne tylko raz. Oba skrypty sekretów
wykonują zmianę w klastrze, dlatego przed użyciem należy uzupełnić lokalny,
ignorowany przez Git plik opisany w `SECRETS.md`. Aktualizacja Secretu nie zmienia
automatycznie hasła istniejącego użytkownika PostgreSQL.

## Wersjonowanie i aktualizacja

Manifesty Kubernetes wskazują pełną wersję semantyczną, a nie `latest`. Aby wydać
nową wersję:

1. połącz przetestowane zmiany z `main`;
2. utwórz tag, na przykład `v0.2.0`;
3. poczekaj, aż workflow opublikuje oba obrazy;
4. zmień oba obrazy w manifestach na `0.2.0` w osobnym Pull Requeście.

W środowisku wymagającym ścisłej niezmienności można zastąpić tag wersji digestem
`@sha256:...` widocznym w GHCR. Tag `main` służy do podglądu najnowszej wersji, nie
do stabilnych wdrożeń. `latest` jest wygodnym wskaźnikiem ostatniego wydania, ale
również nie powinien być używany w manifestach produkcyjnych.

## Docker Compose

`compose.yaml` celowo nadal buduje backend i frontend z lokalnych Dockerfile. Jest
to prosty tryb pracy nad kodem i awaryjna droga uruchomienia bez GHCR:

```powershell
docker compose up --build
```

Kubernetes korzysta natomiast z wcześniej zbudowanych, wersjonowanych artefaktów.
Dzięki temu każdy klaster uruchamia dokładnie to samo wydanie, zamiast polegać na
obrazach istniejących tylko w pamięci konkretnego komputera.

## Automatyzacja wydania i wdrożenia

Pełny proces lokalnych testów, Pull Requesta, squash merge, wydania `v0.1.0`,
publikacji obrazów i wdrożenia można uruchomić z katalogu głównego jednym
interaktywnym poleceniem:

```powershell
.\scripts\release-workflow.ps1 -Stage All
```

Skrypt zatrzymuje się przed każdą operacją zmieniającą Git, GitHub, GHCR albo
klaster i wymaga jawnego potwierdzenia. Etapy można bezpiecznie wznawiać osobno.
Szczegóły, wymagania oraz wszystkie bramki akceptacji opisuje
[docs/release-workflow.md](../../docs/release-workflow.md).

## Rozważone warianty

| Wariant | Zalety | Wady | Decyzja |
| --- | --- | --- | --- |
| GHCR + GitHub Actions | Naturalna integracja z repozytorium, `GITHUB_TOKEN`, wersje, multi-arch i poświadczenia | Pierwsza publikacja i ustawienie widoczności wymagają właściciela repozytorium | Wybrany |
| Docker Hub | Powszechny i prosty w użyciu | Osobne konto, token oraz konfiguracja sekretu CI | Odrzucony dla tego projektu |
| Lokalny build/import do klastra | Działa bez rejestru | Nie jest powtarzalny i wiąże wdrożenie z jednym komputerem | Tylko diagnostyka lokalna |
| Prywatny rejestr w klastrze | Pełna kontrola | Znacznie większy koszt utrzymania, certyfikatów i poświadczeń | Nieproporcjonalny koszt operacyjny dla tego modelu wdrożenia |
