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

In progress / must be verified before delivery:
- Webhook delivery/configuration and granular access overrides.
- SSO/MFA client flows and provider activation documentation.
- Backup execution/restore verification, observability and cloud deployment profiles.
- Full web/mobile/emulator regression checks and final Android test artifact.
- Antenna QR diagnosis, deliberately postponed until all additions are present.

Free test deployment: `scripts/start-free-test.ps1` downloads the official cloudflared Windows release, verifies its published SHA256, verifies backend readiness and anonymous rejection, and starts a hidden temporary HTTPS tunnel. `scripts/stop-free-test.ps1` stops only the recorded RadioTech tunnel process. No cloud compute or paid identity plan is provisioned.

Sources: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/ and https://developers.cloudflare.com/tunnel/downloads/ . The URL changes between tunnels, the PC must stay on, no uptime SLA is included, and existing Firebase usage remains subject to that project's quotas and billing configuration. Production requires a stable company-owned domain and a deployment account managed by the buyer.
