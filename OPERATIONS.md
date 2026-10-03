# RadioTech Control Room — Operations

## Architecture

The application is a Spring Boot 4 / Java 21 service with a Thymeleaf NOC web client, Firebase Authentication, Firestore and Firebase Cloud Messaging.

The web client is the executive Control Room. The same REST API is exposed to the operator mobile application through `/api/operator/**`.

## Required production configuration

Do not ship a Firebase Admin service-account JSON inside the application package. Use Google Application Default Credentials or inject `GOOGLE_APPLICATION_CREDENTIALS` / `FIREBASE_SERVICE_ACCOUNT_JSON` as a secret.

Set:

- `RADIOTECH_FIREBASE_WEB_API_KEY`
- `RADIOTECH_CAPO_EMAIL`
- `RADIOTECH_CORS_ORIGINS`
- `RADIOTECH_GEOFENCE_RADIUS_METERS` (positive distance in meters; defaults to `250`)
- `RADIOTECH_FIRESTORE_SEED=false`
- `RADIOTECH_BOOTSTRAP_TENANT_ID` for CAPO bootstrap (no implicit tenant)
- `RADIOTECH_TENANT_INVITE_SECRET` with at least 32 random characters
- `RADIOTECH_BOOTSTRAP_SECRET` for the one-time CAPO bootstrap

## Authentication

Web login accepts email/password at `POST /api/auth/login`. The backend exchanges credentials with Firebase Identity Toolkit and then verifies the resulting Firebase ID token server-side.

Mobile clients should authenticate with Firebase and send `Authorization: Bearer <Firebase ID token>` to the API.

The API derives tenant scope only from the verified Firebase custom claim
`tenantId`. `X-Tenant-Id` and body fields cannot change that scope. Managers issue
expiring signed registration invitations for their own tenant; pending operators
receive the `OPERATOR` role claim only after approval.

The backend converts Firebase custom claims into RBAC authorities. Recommended roles:

- SUPER_ADMIN
- ADMIN
- CHIEF_EXECUTIVE
- NETWORK_MANAGER
- ENGINEER
- OPERATOR
- VIEWER

Web sessions use the Firebase refresh-token flow at `POST /api/auth/refresh`.
The Control Room refreshes expired ID tokens automatically and only clears the
session when the refresh token is invalid.

## Operator workflow

1. Executive creates a task at `POST /api/tasks`.
2. The task references `operatorId` and optionally `antennaId`.
3. The operator receives a push notification through FCM.
4. The operator uses `/api/operator/me/tasks` and accepts/starts/completes the task.
5. The operator submits a maintenance report through `/api/operator/tasks/{id}/report`.
6. The executive reviews reports through `/api/reports` and can approve or reject them.

Operator QR badges are one-time credentials. New badges expire after 30 days,
are consumed at the first successful QR login, and must be regenerated after
use or loss.

## Maintenance reports

Reports are stored in the `maintenanceReports` collection and support measurements, materials, attachments, GPS coordinates and review status.
The mobile response includes a signed verification URL displayed as a QR after
successful synchronization. Public `GET /api/v1/reports/verify/{token}` checks
the HMAC and immutable report hash, and returns only validity, report ID and
submission time. Configure `RADIOTECH_REPORT_VERIFICATION_SECRET` with at least
32 random bytes and `RADIOTECH_PUBLIC_BASE_URL` with the canonical HTTPS origin
before accepting report submissions. Set `FIREBASE_PROJECT_ID` and
`FIREBASE_STORAGE_BUCKET` to the actual Firebase project and bucket. Readiness
stays down until the verification key, HTTPS origin, and bucket are valid.
Rotating the secret invalidates previously
issued verification QR codes; retain the active key for the verification life
of issued reports or plan a versioned key rotation.
Task-bound report submission requires GPS coordinates and performs the geofenced
check-out before moving the task to `REPORT_SUBMITTED`. Configure
`RADIOTECH_GEOFENCE_RADIUS_METERS` for the deployment's operating policy; the
default is 250 meters. Verify antenna coordinates and field accuracy before
choosing a production value.

The Flutter client stages report photos, signature and generated PDF under
application support storage before upload. Pending uploads are retried with the
same idempotency key; transient retries use capped exponential backoff and stop
after eight attempts. Permanent 4xx/conflict actions remain visible for manual
retry or explicit removal from the mobile Control Room.

The backend accepts report attachment URLs only for the configured Firebase
Storage bucket and the authenticated tenant/operator path, with a Firebase
download token. Firestore stores references, not image data. This checks URL
scope and structure; it does not fetch and hash Storage object bytes, so object
existence and byte-level integrity still require a Storage-side verification
step before making attachment integrity claims.
Storage rules accept only images and PDF files below 10 MB under
`tenants/{tenantId}/maintenance-reports/{operatorUid}/`.

## Deterministic alerts

Managers can evaluate persisted tenant data with `POST /api/alerts/evaluate`
(also available under `/api/v1`). Evaluation creates or updates stable alert
records for task SLA risk/breach, low inventory and active incidents, and
resolves generated alerts when their source condition clears. Repeated
evaluations are deduplicated. This is a manager-triggered operation; no
background scheduler is configured. Alerts for offline assets or failed tasks
are not generated because the current persisted data does not provide a
reliable signal for those conditions.

## Skills and dispatch

Technician skills and dispatch recommendations must remain scoped to the
authenticated tenant. Dispatch should remain advisory: task assignment requires
an explicit manager action. Do not rank by distance unless a current,
tenant-scoped technician location signal is available; last-seen timestamps
alone are not location data.

## Telecom Tools

The backend exposes:

- `/api/telecom-tools/fspl`
- `/api/telecom-tools/eirp`
- `/api/telecom-tools/link-budget`
- `/api/telecom-tools/power`
- `/api/telecom-tools/wavelength`
- `/api/telecom-tools/vswr`
- `/api/telecom-tools/snr`
- `/api/telecom-tools/fresnel`
- `/api/telecom-tools/cable-loss`

The Flutter telecom calculators and the backend tools are preserved as operational
helpers. Their outputs are not a substitute for verified equipment data, local
regulatory limits, manufacturer specifications, or an engineering sign-off.

## Run

Development:

```bash
./gradlew bootRun
```

Tests:

```bash
./gradlew test
```

The report/task, cross-tenant operator-mutation, QR lookup, account provisioning,
operator approval and Firebase token integration tests are opt-in and use only
the local demo emulators.
From this project directory on Windows, run:

```powershell
npm run test:firestore
```

The command starts Firestore and Auth Emulators with project `demo-radiotech`,
uses emulator-only credentials and runs report idempotency/payload-conflict,
signed report QR integrity/tampering, the operator field workflow through
approval and closure, incident lifecycle/timeline, check-out/geofence,
cross-tenant FCM/last-seen, operator QR tenant isolation, cross-tenant
credential-reset rejection, operator claim approval/conflict and Firebase
ID-token claim checks. It does not target Firebase live.

## Production checks

`/actuator/metrics` is limited to account-admin roles; report submission and
verification expose low-cardinality counters. Backup, restore, and
incident-response procedures are documented in
`DISASTER_RECOVERY.md`. Production readiness requires a configured backup bucket,
retention policy, IAM review, and a successful restore drill; the application
does not perform live backups automatically.

Before deployment:

- rotate any Firebase Admin credentials that were previously committed or shared;
- set production CORS origins explicitly;
- keep Firestore seed disabled;
- configure Firebase custom claims/RBAC;
- configure Firestore indexes/rules and Firebase Storage rules;
- enable HTTPS;
- use a shared rate-limit store or edge protection before running multiple API instances; the current limiter is per-process;
- deploy an explicit Content-Security-Policy only after moving the inline Control Room scripts/styles to nonce/hash-based assets and accounting for Leaflet/OpenStreetMap origins;
- configure structured application logging and monitoring;
- test web and mobile authentication with real Firebase accounts;
- test task/report lifecycle end-to-end.

## Firebase release checklist

Deploy Firestore rules from this directory with:

```bash
firebase use gestionale-radio
firebase deploy --only firestore:rules
```

Initialize Firebase Storage once in the Firebase Console, choose the production
region, then deploy its rules with `firebase deploy --only storage`.

Deploy reviewed composite indexes with:

```bash
firebase deploy --only firestore:indexes
```

### Legacy tenant migration

Existing records and Firebase claims must be migrated before deploying the
tenant-enforcing rules/API. The Java runner is disabled by default. First run a
dry-run and explicitly confirm that all unmapped legacy records belong to one
tenant:

```powershell
$env:RADIOTECH_TENANT_MIGRATION_ENABLED = "true"
$env:RADIOTECH_LEGACY_TENANT_ID = "<verified-tenant-id>"
$env:RADIOTECH_TENANT_MIGRATION_SINGLE_TENANT_CONFIRMED = "true"
$env:RADIOTECH_TENANT_MIGRATION_APPLY = "false"
./gradlew bootRun
```

After reviewing the dry-run output, apply only when that single-tenant mapping
is correct. Set replace/apply, restart the application once, then disable the
migration flag immediately:

```powershell
$env:RADIOTECH_TENANT_MIGRATION_REPLACE_DEFAULT = "true"
$env:RADIOTECH_TENANT_MIGRATION_APPLY = "true"
./gradlew bootRun
$env:RADIOTECH_TENANT_MIGRATION_ENABLED = "false"
```

Apply is not a cross-service transaction: Firestore batches are committed before
Firebase custom claims are updated. Take and verify a backup, rehearse the exact
mapping in staging, and prepare a restore/reconciliation plan. If an apply run
fails partway through, stop and inspect both Firestore and claims before retrying.

The runner refuses conflicting tenant IDs and is not active during normal startup.
For mixed-tenant legacy data, map records per tenant first; do not use the
single-tenant confirmation. Users must refresh their Firebase ID tokens after
custom claims are migrated. If demo seeding is enabled, also set
`RADIOTECH_FIRESTORE_SEED_TENANT_ID` explicitly.

Enable App Check providers in the Firebase Console for the registered Android,
iOS and Web apps before enabling enforcement. For local development only, use:

```bash
flutter run --dart-define=APP_CHECK_DEBUG=true
```

Production mobile builds should use Play Integrity on Android, DeviceCheck on
iOS and a configured reCAPTCHA v3 site key on Web.

## Sicurezza — modifiche Fase 0

- Ruoli: un solo modello in `security/Role.java`. Un claim assente o sconosciuto non concede piu' nulla (`NONE`); `SUPER_ADMIN` resta distinto da `ADMIN`.
- `/api/operator/**` richiede il ruolo `OPERATOR` (o `ADMIN`/`SUPER_ADMIN`); `VIEWER` non ha piu' accesso.
- `/api/capo/**` e `/api/v1/capo/**` sono riservate a `SUPER_ADMIN`, `ADMIN`, `CHIEF_EXECUTIVE` e `NETWORK_MANAGER`; il profilo letto o aggiornato deve appartenere al tenant del token.
- `/api/auth/link` e `/api/v1/auth/link` richiedono un account-admin; un operatore non puo' associare account Firebase ad altri operatori.
- Auto-registrazione: `POST /api/operator/register` crea l'operatore con stato `IN_ATTESA`. Un manager lo abilita con `POST /api/operators/{id}/approve`, che assegna il claim `OPERATOR` e porta lo stato ad `ATTIVO`. Dopo l'approvazione l'utente deve rifare il login (o rinnovare l'ID token) per ottenere il nuovo claim.
- Rate limit su login, QR login, refresh, verify e bootstrap: `RADIOTECH_RATE_LIMIT_PER_MINUTE` (default 10 per IP ed endpoint). Stato in memoria, quindi per singola istanza.
- `generate-credentials` non riusa piu' account con ruolo diverso da operatore; le password temporanee usano `SecureRandom`.
- La promozione automatica a CAPO via email richiede l'email verificata.
- Tenant: le route protette richiedono il claim Firebase `tenantId`; l'onboarding anonimo rispetto ai claim applicativi richiede un invito HMAC firmato dal manager. Configurare `RADIOTECH_TENANT_INVITE_SECRET` prima di emettere inviti.
- Rules tests: `npm run test:rules` avvia emulatori Firestore/Storage e prova accesso cross-tenant e spoofing.
