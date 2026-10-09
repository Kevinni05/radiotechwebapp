# Customer-owned Enterprise deployment

RadioTech is delivered as a reusable enterprise product. Production resources
must be owned by the purchasing company: Firebase / Google Cloud project,
service accounts, domain, TLS, backend hosting, backups, Android signing and
mobile distribution.

The seller's development Firebase project and Render service are not production
dependencies for a customer installation.

## Target topology

A standard customer deployment contains:

1. the Spring Boot web application/API, running as a container on the buyer's
   VM, Kubernetes platform, Cloud Run or equivalent;
2. a buyer-owned Firebase project for Authentication, Firestore, Storage,
   Cloud Messaging and App Check;
3. a buyer-owned HTTPS domain terminating TLS in front of the API;
4. buyer-owned backup buckets and retention policies;
5. the Flutter app built against that API and Firebase project, signed with the
   buyer's Android key.

The existing `Dockerfile` and `compose.yaml` are the reference Linux
deployment. `deploy/kubernetes` contains the Kubernetes profile.

## 1. Create the buyer Firebase project

Create the project inside the buyer's Google organization/billing account.

Enable and configure:

- Firebase Authentication and the approved sign-in providers;
- Firestore Native mode in the required region;
- Firebase Storage;
- Firebase Cloud Messaging;
- Firebase App Check;
- App Distribution if internal Android delivery is desired.

Create the Android application with the buyer's final package ID. The mobile
repository must receive the buyer `google-services.json` and regenerated
FlutterFire configuration before release.

Use dedicated service identities. Prefer workload identity where the hosting
platform supports it. If a JSON service-account credential is unavoidable,
store it outside the repository and mount it as a secret.

## 2. Configure the backend

Copy:

```powershell
Copy-Item .env.production.example .env.production
```

Fill every required value. At minimum, production needs:

- `FIREBASE_PROJECT_ID`
- `FIREBASE_STORAGE_BUCKET`
- `RADIOTECH_FIREBASE_WEB_API_KEY`
- `RADIOTECH_PUBLIC_BASE_URL`
- `RADIOTECH_CORS_ORIGINS`
- independent report-verification and tenant-invite secrets
- initial bootstrap tenant values only during first provisioning
- customer branding values

Set `FIREBASE_CREDENTIALS_FILE` outside the env file to the external
buyer-owned credential file when workload identity is not used.

Before starting production:

```powershell
.\scripts\validate-customer-config.ps1 -EnvFile .env.production -FirebaseCredentialsFile C:\secure\customer-firebase.json
```

The validator never prints secret values.

## 3. White-label identity

The following environment variables change the visible web identity without
editing Java or HTML:

- `RADIOTECH_BRAND_NAME`
- `RADIOTECH_BRAND_SUBTITLE`
- `RADIOTECH_BRAND_LOGO_PATH`
- `RADIOTECH_BRAND_COPYRIGHT`
- `RADIOTECH_BRAND_SUPPORT_EMAIL`

Customer logo files are placed under
`src/main/resources/static/assets/brand/` before creating the customer's
immutable image. Logo paths are restricted to that asset area by the
application.

Product-internal identifiers such as API paths, Java package names and
`service=radiotech-backend` are compatibility identifiers and do not appear as
customer branding.

## 4. Deploy Firebase rules and indexes

After reviewing the target project ID:

```powershell
.\scripts\deploy-customer-firebase.ps1 \
  -FirebaseProjectId customer-production-project \
  -ConfirmProjectId customer-production-project
```

The script deploys Firestore rules, Firestore indexes and Storage rules only.
Authentication providers, authorized domains and App Check enforcement must be
verified separately in the buyer Firebase console.

## 5. Start the container deployment

On a buyer-controlled Linux host:

```bash
export FIREBASE_CREDENTIALS_FILE=/secure/customer-firebase.json
docker compose config --quiet
docker compose up --build -d
```

The API binds only to `127.0.0.1:8080` in the reference Compose profile.
Terminate TLS with the buyer's reverse proxy/domain. Do not expose the Spring
port directly to the Internet.

Verify:

```powershell
.\scripts\verify-release.ps1 -BaseUrl https://operations.customer.example
```

The service must report `service=radiotech-backend`, health UP and readiness
UP before the mobile application is released.

## 6. Initial tenant and manager

Use the bootstrap mechanism only for first provisioning. Create the initial
tenant/manager, verify access, then remove/disable the bootstrap secret. Never
leave bootstrap enabled as a standing administration path.

After provisioning, validate tenant isolation using at least two test tenants
before importing real customer data.

## 7. Backups and recovery

Follow `DISASTER_RECOVERY.md`. The buyer owns:

- Firestore export bucket;
- Storage backup bucket;
- retention and immutability policy;
- Auth recovery procedure;
- restore project;
- encryption keys and secret manager.

A release is not considered production-ready until a restore drill has
successfully restored representative tenant data into an isolated project.

## 8. Mobile handoff

The Flutter repository contains `docs/CUSTOMER-PROVISIONING.md`. Build the
mobile client with the buyer Firebase app, HTTPS API URL, brand, Android
application ID and buyer signing key.

The buyer must retain the signing certificate for the lifetime of the Android
application. Firebase App Distribution or Play Console must also belong to the
buyer.

## 9. Enterprise acceptance gate

Before commercial handover, record evidence for:

- backend unit/integration tests;
- Firestore/Storage emulator rules tests;
- browser end-to-end tests;
- container build and readiness;
- authenticated tenant-isolation smoke test;
- backup and restore drill;
- real-device Android login/App Check;
- offline/reconnect behavior;
- GPS task lifecycle;
- report PDF/signature/photos and history;
- FCM notification;
- signed mobile upgrade;
- operator/admin/customer authorization matrix;
- rollback to the previous application image.

Store the exact source commit, container digest, Firebase rules revision,
Android versionCode, signing fingerprint and artifact hashes in the buyer's
change record.

## Handover boundary

After handover the buyer can operate production without access to the seller's
Firebase project, Render account, signing key or service-account credentials.
Optional maintenance/support may be provided separately, but it is not a
technical requirement for the platform to run.
