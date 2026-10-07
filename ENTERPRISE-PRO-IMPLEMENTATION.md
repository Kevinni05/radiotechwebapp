## Aggiornamento 1.4 — 7 ottobre 2026

Implementati calendario personale web/mobile con promemoria backend, widget ora/meteo, filtro e compressione report, badge con validità configurabile e reset password, cancellazione operatori con controlli di sicurezza, magazzino con posizioni/lotti/movimenti/FEFO e aggiornamenti in tempo reale, TelcoTools web. Guida operativa: [AGGIORNAMENTO-OPERATIVO-1.4.md](AGGIORNAMENTO-OPERATIVO-1.4.md).

Backend aggiornato sul servizio locale; collegamento Cloudflare esistente mantenuto. Mobile 1.4.0/2018 installata sul dispositivo collegato. Test unitari backend ed emulatori Firebase superati; 98 test mobile e analisi pulita. Risultati browser e HTTPS nei log `.dist/enterprise-upgrade-*`.

# Enterprise Pro — implementation and activation

The existing web/backend and Flutter projects are being expanded in place. This is a work ledger, not a claim of production certification.

Implemented in the shared backend and both application interfaces:
- Tenant-scoped clients, sites, contracts and contacts.
- Preventive plans, checklists and idempotent generation of assigned tasks.
- Dispatch reservations with overlap locking, skills/expiry checks and explicit outside-shift override.
- SLA acknowledgement, reasoned pause/resume, closure and persisted escalation.
- Suppliers, independent purchase approvals, issued orders, atomic inventory receipts.
- Hours, travel, materials/external costs, approved totals and contract budgets.
- Published technical documents, previous immutable revision snapshots.
- Management indicators from actual task/cost records, bounded-query coverage notices, CSV export.
- Customer role and portal, customer scope verified server-side, scoped requests and published customer documents.
- Technician stock load/reserve/consume/return, central stock updates in the same transaction.
- Handover acknowledgement restricted to the recipient.
- Notification receipt/read timestamps per recipient, separate from FCM delivery results.
- Encrypted mobile offline task packs with actor/tenant binding, 24-hour expiry and logout cleanup.
- Session revocation verified on authenticated backend requests; customer account binding and managed-MFA enforcement.

Also implemented:
- Signed webhook delivery, durable retries, controlled destinations and granular Pro permissions.
- Google/Microsoft SSO and TOTP client flows, with deployment flags and provider activation documentation.
- Encrypted scheduled backups, emulator restore verification, optional telemetry and future cloud deployment profiles.
- Antenna QR generation and resolution, verified against the actual backend.
- First-login QR account linking without a pre-existing authenticated session, versioned badges carrying the HTTPS server, and explicit confirmation of server changes in the mobile test build.
- Shared animated Aurora design, official project logo in the mobile UI/Android launcher/splash, reduced-motion handling and adaptive layouts. The custom desktop cursor was removed at the user's request.
- Durable report snapshots before networking, immutable retries, actor/tenant isolation, serialized offline queue writes and acknowledgement before attachment cleanup.

Delivery checks and the current test artifact are documented in `TEST-GRATUITO-ENTERPRISE-PRO.md`. External identity providers, managed MFA plans, production domains, collectors and company integrations require the buyer's accounts/configuration; their preparation does not mean they have been activated.

Free test deployment: `scripts/start-free-test.ps1` downloads the official cloudflared Windows release, verifies its published SHA256, verifies backend readiness and anonymous rejection, and starts a hidden temporary HTTPS tunnel. `scripts/stop-free-test.ps1` stops only the recorded RadioTech tunnel process. No cloud compute or paid identity plan is provisioned.

Sources: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/ and https://developers.cloudflare.com/tunnel/downloads/ . The URL changes between tunnels, the PC must stay on, no uptime SLA is included, and existing Firebase usage remains subject to that project's quotas and billing configuration. Production requires a stable company-owned domain and a deployment account managed by the buyer.
