# Dashboard read budget

Dashboard summaries now use a tenant-scoped cache with a 15-second maximum age after computation, coalesced concurrent requests and a 512-tenant bound. Responses include `asOf` and `cacheMaxAgeSeconds`. Failed computations are not cached. Invalidating an in-flight computation removes its entry; its eventual completion cannot replace a fresh entry.

Every authenticated API write attempt invalidates the corresponding tenant after controller completion, including failures that might have followed a partial write. The tenant is taken from the verified request attribute, never an arbitrary header. Changes from mobile, background automation, another server instance or direct Firestore access become visible at the next cache expiry. This cache is per instance and is not a distributed invalidation system.

The intervention total uses a Firestore count aggregation. Other summary queries project only the status, due date, quantity or removal fields they need. This reduces transferred content and avoids loading report bodies, attachments, badges or token lists. Projections still incur document reads on a cache miss. Existing case-insensitive asset/operator states, legacy task aliases, SLA calculation and logically removed report semantics are preserved without introducing a production composite-index dependency.

Unit tests cover expiry, tenant separation, immutable responses, failure recovery, bounded eviction, concurrent readers and invalidation during computation. Emulator tests cover the real projected/count queries and legacy semantics. A write-interceptor regression test includes a partial failure and a forged tenant header.

B06 remains open until production-like workloads demonstrate the target reduction in billed reads and a stable p95. In particular, the first request after expiry still scans projected task/status records; large tenants require maintained summary projections or canonical status migration, measured against a baseline. A theoretical cache hit ratio is not evidence of the plan's 70% target.
