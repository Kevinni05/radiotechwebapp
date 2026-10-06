# RadioTech — rilascio web, API e mobile

## Ambiente rilevato il 3 ottobre 2026

- Progetto Firebase: `gestionale-radio`; Firestore Native `(default)` in `eur3`.
- Bucket configurato dal client: `gestionale-radio.firebasestorage.app`.
- Hosting Firebase: `https://gestionale-radio.web.app`. `/api/v1/health` risponde 404: questo dominio non ospita attualmente l'API Spring.
- Cloud Run Admin API e Compute Engine API risultano disabilitate nel progetto.
- Credenziali ADC locali presenti. Non sono state copiate nel repository o negli artefatti.
- Nei due progetti non è stato trovato un dominio API di produzione né un upload keystore Android. Non è stato effettuato un deploy live o modificato alcun dato Firebase.

## Verifica locale

Java 21, Node 22, Firebase CLI 15.26.0, Flutter 3.44.9 / Dart 3.12.2 e Android SDK 36.
La CLI Firebase è uno strumento esterno: installarla separatamente (`npm install -g firebase-tools@15.26.0`). La versione CLI ha proprie dipendenze da monitorare; `npm audit` del repository non certifica i tool globali, le dipendenze Java o Flutter.

```powershell
.\gradlew.bat test bootJar --no-daemon --console=plain
npm ci
npm audit --audit-level=high
npm run test:rules
npm run test:firestore
npx playwright install chromium
npm run test:web
```

Le prove Firebase usano esclusivamente `demo-radiotech` e gli emulatori locali. Il browser avvia il JAR senza Firebase e simula i contratti API: verifica layout, routing, autenticazione HTTP, risposte task, incidenti, abilitazioni e escaping, ma non sostituisce l'accettazione sul tenant staging con utenti reali.

I risultati e gli hash degli artefatti verificati sono in [VERIFICATION.md](VERIFICATION.md). Dopo un aggiornamento dei plugin Flutter, eseguire `flutter clean` e `flutter pub get` se Gradle riutilizza classi obsolete: la build pulita con i plugin aggiornati è stata verificata su Android.

## Server Linux con Docker e Caddy

1. Copiare `.env.production.example` in `.env.production` e compilare tutti i valori. Il profilo `production` rifiuta emulatori, seed, migrazioni, chiavi brevi/riutilizzate, origin CORS non HTTPS e origin pubblici placeholder.
2. Generare **due chiavi indipendenti**, conservandole nel secret manager. Per ciascuna: `[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))`. Non cambiare la chiave report senza un piano per i QR già emessi.
3. Impostare nell'ambiente Compose `FIREBASE_CREDENTIALS_FILE` con il percorso di un JSON ADC/service account esterno al repository. Nel container sarà montato come secret in sola lettura. Per cloud preferire workload identity.
4. `docker compose config --quiet`, poi `docker compose up --build -d`. Il backend è esposto solo su `127.0.0.1:8080`, eseguito da utente senza privilegi, con filesystem in sola lettura. Non pubblicare questa porta direttamente.
5. Eseguire Caddy sullo stesso host con `deploy/Caddyfile` e `RADIOTECH_DOMAIN` impostato al dominio approvato. DNS, porte 80/443 e certificato devono essere disponibili. Il Caddyfile punta al backend locale.
6. Se l'indirizzo sorgente del proxy nel container è una rete Docker, configurare `RADIOTECH_TRUSTED_PROXY_REGEX` per **quel proxy specifico**, e verificare l'IP risultante. Non fidarsi indiscriminatamente di tutti gli header forwarded. Questo è necessario anche per il rate limit per IP.
7. Verificare `scripts/verify-release.ps1 -BaseUrl https://DOMINIO-REALE`.

Docker non è disponibile in questa workstation: il Dockerfile e Compose sono predisposti, la build container è un gate CI e deve passare prima del rilascio. Il rate limiter attuale è locale al processo; più repliche richiedono protezione condivisa all'edge o un limiter distribuito. Non scalare senza risolvere questo punto.

## Cloud Run nello stesso progetto Firebase

Per il pacchetto con AI centrale, warm-up del modello, turni/pause e segnalazioni seguire [ENTERPRISE_AI.md](ENTERPRISE_AI.md). La verifica aggiuntiva ha rilevato `billingEnabled=false` nel progetto: la pubblicazione cloud richiede un hosting attivo o l'abilitazione del billing.

Il Dockerfile è utilizzabile anche su Cloud Run. Prima servono: billing, API Run/Build/Artifact Registry, account di servizio dedicato e IAM per Firestore/Auth/Storage/FCM, secret manager, dominio/URL pubblico e ambiente staging. Non usare credenziali JSON nell'immagine.

Eseguire il primo rollout su un servizio staging dedicato, usando il profilo `production`, i valori della configurazione esempio e secret references. Per la prima release mantenere una replica massima finché il rate limit condiviso non è configurato. Configurare le sonde su `/actuator/health/liveness` e `/actuator/health/readiness`. La pubblicazione dell'API è compatibile con richieste anonime alla piattaforma: le route business restano protette dai token Firebase e dai controlli tenant del servizio.

Non è ancora stata scelta/attivata questa destinazione. Eventuali rewrite di Firebase Hosting devono puntare al servizio realmente deployato; non pubblicare la pagina Thymeleaf come semplice HTML statico, perché necessita del backend e dei nonce CSP per risposta.

## Dati, sicurezza e accettazione

Distribuire regole e indici dopo avere verificato la migrazione dei dati/claims esistenti, con backup e restore drill secondo `DISASTER_RECOVERY.md`. Il nuovo rilascio non esegue automaticamente migrazioni o seed.

Bootstrap manager solo quando necessario; disabilitare immediatamente il secret dopo il provisioning. Verificare registrazione con invito, approvazione operatore e refresh dei custom claims. App Check per Firestore/Storage richiede provider e enforcement nella console Firebase; il backend REST autentica ID token/RBAC ma non verifica attualmente token App Check. Nessuna attestazione di enforcement API viene implicata dal client.

Le risposte operatori e dashboard rimuovono token QR/FCM. Il badge si recupera tramite l'endpoint dedicato con permesso di gestione utenti; un viewer non può leggere direttamente i documenti operatori in Firestore né recuperare il badge. Le credenziali iniziali sono mostrate solo durante la creazione amministrativa. Verificare che eventuali client esterni usino il nuovo endpoint prima di distribuire le regole.

Provare su staging: assegnazione task con antenna e coordinate valide → accettazione → trasferimento → check-in GPS → lavoro → report con firma/allegati → revisione → chiusura. Provare anche GPS negato, report rifiutato, rete assente, cambio account, scadenza del token e conflitti di idempotenza. Le operazioni offline create da un'altra identità restano in attesa senza consumare i ritentativi; quelle legacy senza identità devono essere reinserite dopo verifica.

Gli allegati restano in storage applicativo locale, con backup Android disabilitato. Il client non offre ancora cifratura applicativa delle bozze/allegati: per flotte enterprise valutare gestione dei dispositivi, cifratura del sistema, blocco schermo e policy di cancellazione. Non equiparare questi controlli a certificazione o penetration test.

## Mobile

Nel progetto `gestionale_radio`:

```powershell
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Per il rilascio: compilare `android/key.properties` dal modello usando la **chiave di upload esistente** e conservare il file fuori Git; in alternativa usare le variabili `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Gli application ID esistenti sono conservati per compatibilità Firebase e futuri aggiornamenti.

```powershell
.\scripts\build-release.ps1 -ApiBaseUrl https://DOMINIO-REALE -BuildNumber 2
```

Lo script verifica API e readiness, analisi/test, firma AAB e genera simboli di debug da conservare privatamente. HTTPS e App Check senza debug sono richiesti in release; il server compilato nel binario non può essere sostituito attraverso preferenze salvate.

iOS richiede macOS/Xcode, signing team/provisioning, configurazione Firebase/APNs e capability Push Notifications. I permessi di fotocamera, posizione e fotografie sono predisposti. Non è stata validata una build iOS su Windows. Il ramo Android usa temporaneamente KGP/DSL compatibile con Flutter 3.44.9; la migrazione built-in Kotlin richiede Flutter 3.47 o successivo, da effettuare con la relativa verifica di build.

## Rollback e osservabilità

Archiviare hash SHA-256 del JAR, APK/AAB e immagine, configurazione senza segreti e versione delle regole. Il backend aggiunge `X-Request-Id` e il profilo produzione emette log JSON ECS. Configurare allarmi su errori HTTP, readiness, fallimenti FCM e backlog di report.

Per un rollback applicativo riavviare l'immagine/JAR precedente con i secret conservati. Non ripristinare automaticamente regole meno restrittive né dati live. Regole, schema, claims e chiave report vanno riconciliati separatamente. Le due pipeline devono essere registrate nei rispettivi repository; il progetto mobile ha ora un repository Git locale sul ramo `main`, da collegare al remoto. Non sono stati creati commit, eseguiti push o pubblicati repository.

Riferimenti: [Springdoc/Spring Boot](https://springdoc.org/), [Firebase Admin release notes](https://firebase.google.com/support/release-notes/admin/java), [Flutter Android signing](https://docs.flutter.dev/deployment/android), [compatibilità built-in Kotlin](https://docs.flutter.dev/release/breaking-changes/migrate-to-built-in-kotlin/for-app-developers).
