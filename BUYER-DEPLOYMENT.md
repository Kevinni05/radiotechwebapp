# RadioTech Enterprise Pro — buyer deployment kit

This repository is designed to be delivered to a customer as a white-label,
customer-owned deployment. The customer can run the application in its own cloud
account or on company-managed infrastructure while keeping identity, data,
storage, DNS, certificates, signing keys and observability under customer
control.

## Supported ownership models

### 1. Customer-owned cloud

Recommended production model.

- Backend container in the customer's GCP, Azure, AWS or Kubernetes account.
- Customer-owned Firebase/GCP project for Authentication, Firestore, Storage,
  App Check and Cloud Messaging.
- Customer-owned DNS, TLS, secret manager, container registry and monitoring.
- Customer-owned Android signing key and Firebase App Distribution / Play
  Console account.
- Optional customer-owned SSO provider (Google Workspace or Microsoft Entra ID).
- Optional private Ollama deployment for the AI module.

No RadioTech developer credential is required at runtime.

### 2. Company infrastructure / private datacenter

- Run the backend container with Docker Compose or Kubernetes.
- Terminate TLS on the company's reverse proxy / ingress.
- Keep secrets in the company's vault or secret manager.
- Restrict outbound egress to the customer's Firebase/GCP endpoints and any
  explicitly enabled integrations.
- Firebase services still live in the customer's own cloud project.

A completely disconnected/offline installation with no Firebase/GCP dependency
is a different product profile because Authentication, Firestore, Storage,
Messaging and App Check would need alternative provider implementations.

## White-label configuration

The following variables customize visible product identity without source-code
changes:

```env
RADIOTECH_BRAND_NAME=Acme Field Operations
RADIOTECH_BRAND_SUBTITLE=Network Operations
RADIOTECH_BRAND_COMPANY=Acme S.p.A.
RADIOTECH_BRAND_SUPPORT_EMAIL=it-support@acme.example
RADIOTECH_BRAND_LOGO_PATH=/assets/brand/customer-logo.png
RADIOTECH_BRAND_LEGAL_TEXT=© 2026 Acme S.p.A. - Uso interno riservato
```

The logo path must reference an application-local asset. Buyer-specific logo
assets can be added during the branding delivery step without changing security
identifiers or API contracts.

## Customer bootstrap checklist

1. Create separate customer-owned Firebase projects for staging and production.
2. Enable Authentication providers, Firestore, Storage, App Check and FCM.
3. Create least-privilege backend workload identity/service account.
4. Create the production domain and TLS route.
5. Copy `.env.production.example` into the customer's secret/config system.
6. Generate independent random report-verification and tenant-invitation keys.
7. Set exact HTTPS CORS/public origins.
8. Deploy Firestore rules/indexes and Storage rules from this repository.
9. Build and push the backend image to the customer registry using an immutable
   digest.
10. Deploy to staging using Docker Compose, Kubernetes or the selected managed
    container platform.
11. Provision the first manager, then disable the bootstrap secret.
12. Create a customer-owned Android signing key and register its fingerprints
    with Firebase/App Check.
13. Build the mobile app against the customer's production API/Firebase project.
14. Publish the mobile build through the customer's App Distribution or Play
    account.
15. Execute the acceptance suite before production promotion.

## Infrastructure assets already included

- Hardened multi-stage Docker image.
- Docker Compose production profile.
- Kubernetes deployment with probes, rolling updates, non-root execution,
  read-only root filesystem and PodDisruptionBudget.
- Caddy reverse-proxy example.
- Render profile for demonstration/small hosted deployments.
- Firebase security rules and emulator tests.
- Backend, web, rules, container and reproducibility CI gates.
- Disaster recovery, operations, verification and security documentation.

## Required buyer secrets

At minimum:

```text
FIREBASE_PROJECT_ID
FIREBASE_STORAGE_BUCKET
RADIOTECH_FIREBASE_WEB_API_KEY
RADIOTECH_PUBLIC_BASE_URL
RADIOTECH_CORS_ORIGINS
RADIOTECH_REPORT_VERIFICATION_SECRET
RADIOTECH_TENANT_INVITE_SECRET
Firebase workload identity / service-account binding
Android signing key + passwords
```

Optional modules add their own customer-owned secrets for SSO, webhooks,
telemetry and AI.

Secrets must never be committed to either repository.

## Mobile productization

The delivered mobile binary must be built per customer using:

- customer Firebase Android/iOS app registrations;
- customer API base URL;
- customer signing identity;
- customer application/bundle identifier for a new deployment;
- customer App Check configuration;
- customer-owned distribution account.

For an existing installed fleet, changing the Android application ID or signing
key creates a new app and cannot be treated as an in-place update. Perform that
migration only as part of a controlled customer cutover.

## Enterprise acceptance gate

A release is ready for customer handover only when:

- backend unit/integration tests are green;
- Firebase rules/emulator tests are green;
- browser E2E tests are green;
- production container build is green;
- mobile analysis/tests/build are green;
- backend readiness and identity probes pass on staging;
- RBAC and tenant isolation are verified with real customer roles;
- mobile login, offline/reconnect, GPS, report, attachments, PDF/history and push
  notification flows pass on a real device;
- backup and restore drill succeeds;
- rollback procedure is documented and tested;
- all runtime credentials are owned by the customer.

## Handover package

The commercial delivery should contain:

- source repositories or agreed source escrow;
- immutable backend image digest;
- signed mobile artifacts;
- configuration inventory with no secret values;
- deployment/runbook documentation;
- architecture and data-flow diagrams;
- backup/restore and rollback procedure;
- test/acceptance report;
- SBOM and dependency inventory if required by procurement;
- ownership matrix for domains, Firebase/GCP, registry, signing, SSO and
  observability.

The product can therefore remain hosted for the customer or be transferred to
customer infrastructure without coupling production runtime to the developer's
personal accounts.
