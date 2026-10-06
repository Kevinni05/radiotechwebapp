# Security review handoff — ASVS 5.0.0

This is an evidence checklist for an independent reviewer, not ASVS certification or a penetration-test report. Source: https://owasp.org/projects/asvs .

| Area | Implemented control / evidence | Production verification |
|---|---|---|
| Authentication | Firebase signatures, disabled-user/token-revocation checks, no role for unapproved identities, managed TOTP challenge/enrollment | Activate company SSO/MFA and verify recovery, phishing resistance and lockout |
| Session/device control | Fresh-login device binding, signed device ID, tenant/UID/device-state checks, targeted device and global session revocation | Test a real lost-device scenario and revocation after refresh; legacy clients require global revocation |
| Authorization | Exact tenant checks on records/references, customer binding, published document audience, own technician resources, configurable Pro permission allowlist | Independently review existing role APIs as well as Pro permissions |
| Business integrity | Transaction versions/idempotency, independent purchase/cost approval, atomic stock movements, dispatch locking/skills, immutable document revisions | Concurrency/load tests at company data volume; quotas and index deployment |
| Encoding/validation | Shared server schemas, unknown-field rejection, numeric/date/URL/reference checks, escaped HTML, bounded inputs and CSV formula protection | Fuzz all endpoints, inspect export behavior in spreadsheet clients |
| Outbound integrations | Company endpoint allowlist, public-IP check, pinned-address TLS with hostname/SNI verification, redirect rejection, HMAC, bounded deadlines and durable retries | Network egress allowlist, secret rotation, receiver replay prevention |
| Offline/privacy | Encrypted pack storage, tenant/UID binding, 24h expiry, logout cleanup, no offline privilege escalation | Revocation cannot erase already downloaded data while a device stays offline; set company retention and MDM policy |
| Audit/observability | Transactional Pro audit before/after; dedicated recipient receipt records; request correlations; protected metrics; tracing bridge | SIEM retention, alert routing, audit immutability and company telemetry configuration |
| Deployment/secrets | Production validation, non-root read-only image, probes, digest-based rollout/rollback, separate environments | Buyer cloud IAM, stable HTTPS, secret manager, SBOM/image scanning |
| Recovery | AES256-GCM local test exports, verification and emulator-only document restore drill | Offsite retention; provider/MFA/hash parameters; consistent snapshot and full files/password restore |

Run backend tests, Firestore/Auth integration tests, rules tests, web tests, Flutter analysis/tests and build verification before promotion. Capture the final run results separately; features requiring external provider configuration must be acceptance-tested after activation. No production resources, paid identity upgrades or chargeable cloud instances were enabled by this implementation.
