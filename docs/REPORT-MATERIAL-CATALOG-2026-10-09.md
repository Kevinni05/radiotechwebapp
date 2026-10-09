# Report materials and warehouse approval

The mobile report form used four fixed material names and submitted only name and quantity. Warehouse consumption intentionally requires stable inventory IDs or SKUs. This made reports with the old suggestions fail approval and remain `APPROVAL_PENDING`.

Operators now load the tenant's active catalog from `GET /api/v1/operator/me/inventory`. The response exposes only ID, SKU, name, quantity and unit. The form searches existing items, sends `inventoryId` and SKU, and caches the last catalog per tenant and operator for offline work. Old drafts match unique exact names; unmatched selected items must be removed and replaced with an actual catalog item.

For existing reports, the web approval dialog loads `GET /api/v1/reports/{id}/review-context`. A reviewer with `REPORT_APPROVE` permission must map unresolved rows to actual warehouse items, or explicitly confirm that the material was not taken from company stock. An exact unique name can be preselected; different material names are never treated as equivalent automatically. Quantities remain those originally declared.

The signed `materialsUsed`, signature, PDF and integrity hash are unchanged. Review mappings, reviewer and time are stored separately, alongside normalized consumption materials. Consumption remains atomic, tenant scoped and idempotent by report ID. A retry reuses stored decisions; once inventory has been consumed, a different mapping is rejected. Cancelling the dialog does not approve or consume anything.

For the previously failed report, open **Report → Riprendi approvazione**, confirm the actual articles used, and approve. In particular, `Cavo RG-213` is not automatically equated to `Cavo coassiale`; `Antennino` and `Fusibile 5A` are not fabricated as new stock.

Android version: **1.5.1+2022**, using the existing production certificate and the Render API origin. Install the updated APK to replace the old fixed suggestions.

Regression checks cover stable material submission on mobile, cancellation and unresolved selections in the browser, signed-data preservation, tenant isolation, and approval retries after task finalization fails. See the corresponding Flutter, Playwright and Firestore emulator tests.
