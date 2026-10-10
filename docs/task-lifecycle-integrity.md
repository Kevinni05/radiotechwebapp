# Atomic task lifecycle

Check-in, check-out, status transitions and task deletion now commit their audit events in the same Firestore transaction as the operational change. Check-in/check-out transactions re-read the assignment and asset coordinates before any writes, so concurrent reassignment or an asset edit cannot bypass the GPS/tenant checks. An already completed operation does not create another audit event. Status idempotency receipts are committed with the state change and audit.

Generic status changes cannot set CHECKED_IN or COMPLETED: the GPS endpoints are required. Operator status mutations re-check assignment within the transaction, including replay requests. This deliberately closes generic completion without GPS; an authorized exceptional completion flow still requires a separate permission, reason and audit design before implementation.

Emulator coverage includes the full GPS lifecycle, repeated check-in/out, generic completion rejection without a receipt, real persisted actor/action audit records, audited deletion and reassignment between the initial read and transactional mutation. This closes these specific race/audit gaps; other legacy mutation services and Firebase external-identity reconciliation remain B04 acceptance work.
