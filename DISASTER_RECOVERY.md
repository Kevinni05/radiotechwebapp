# Radio Tech Backup and Recovery Runbook

This runbook describes local/staging-safe recovery procedures for the Firebase-backed platform. It is not a claim that production backup, retention, or restore drills are already enabled. A production owner must configure the buckets, IAM, retention, and schedules before relying on the targets below.

## Recovery targets

Set and approve these values for each environment:

- RPO: maximum acceptable data loss for Firestore, Storage, and Auth.
- RTO: maximum acceptable time to restore API, rules, indexes, files, and claims.
- Retention: minimum number of daily and monthly exports retained in a separate project or bucket.

The application must remain in maintenance mode during a restore. Do not run the migration runner, seed data, or normal writes against a partially restored project.

## What must be backed up

1. Firestore documents and indexes.
2. Firebase Storage objects, including report attachments and generated PDFs.
3. Firebase Authentication users and provider configuration.
4. Firestore rules, Storage rules, `firebase.json`, and `firestore.indexes.json`.
5. Environment configuration names and secret references, never secret values in source control.
6. Reviewed application artifacts and the exact commit used for the environment.

## Firestore export

Use a dedicated backup bucket with retention and restricted service-account access. Run from an authenticated operator workstation or a scheduled trusted job:

```powershell
$project = $env:FIREBASE_PROJECT_ID
$bucket = $env:FIRESTORE_BACKUP_BUCKET
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
gcloud firestore export "gs://$bucket/firestore/$stamp" --project $project
```

Record the export path, project, operator, timestamp, and command output in the change record. Never point this command at the production project until the backup bucket and retention policy have been reviewed.

## Firestore restore

1. Freeze application writes and disable scheduled jobs.
2. Create a new isolated restore project when possible.
3. Import the selected export:

```powershell
gcloud firestore import "gs://$env:FIRESTORE_BACKUP_BUCKET/firestore/<export-stamp>" --project $env:FIREBASE_RESTORE_PROJECT_ID
```

4. Deploy the reviewed rules and indexes to the restore project.
5. Verify tenant isolation, task/report counts, audit records, idempotency keys, and representative attachments.
6. Reconcile Firebase Auth claims before reconnecting users.
7. Switch traffic only after smoke tests and an explicit approval.

Firestore export/import does not restore external Firebase Auth state or application secrets. Treat those as separate recovery artifacts.

## Storage backup and restore

Use a separate bucket or a provider-managed backup policy. For a controlled staging rehearsal:

```powershell
gcloud storage cp --recursive "gs://$env:FIREBASE_STORAGE_BUCKET/" "gs://$env:FIREBASE_BACKUP_BUCKET/storage/<export-stamp>/"
gcloud storage cp --recursive "gs://$env:FIREBASE_BACKUP_BUCKET/storage/<export-stamp>/" "gs://$env:FIREBASE_RESTORE_BUCKET/"
```

Verify object content type, size, tenant path, and download authorization after restore. Do not copy objects across tenants or restore directly over live data without an approved change.

## Firebase Auth recovery

Export users to an access-controlled temporary file and delete it after the recovery exercise:

```powershell
firebase auth:export .\auth-users.json --project $env:FIREBASE_PROJECT_ID --format=json
firebase auth:import .\auth-users.json --project $env:FIREBASE_RESTORE_PROJECT_ID --hash-algo=SCRYPT
Remove-Item .\auth-users.json
```

Password hashes and provider settings are sensitive. Never commit the export, print it in CI logs, or send it through chat. Custom claims must be re-applied from an approved tenant/role source and users must refresh their ID tokens.

## Configuration recovery

1. Check out the exact reviewed application commit.
2. Recreate environment variables from `.env.example` and the secret manager references.
3. Inject credentials through workload identity, ADC, or a secret store.
4. Keep `RADIOTECH_FIRESTORE_SEED=false` during recovery.
5. Keep tenant migration disabled unless the migration procedure is explicitly being run.
6. Verify CORS, geofence, GPS accuracy, SLA warning, rate limit, and emulator/project IDs before startup.

## Incident response

1. Declare the incident and record UTC time, project, suspected scope, and incident owner.
2. Stop destructive writes and revoke compromised credentials or sessions.
3. Preserve audit logs, application logs, recovery output, and relevant request IDs.
4. Classify the event: auth, tenant isolation, data corruption, storage loss, dependency outage, or application defect.
5. Select the last known-good export and rehearse the restore in isolation.
6. Validate rules, claims, tenant boundaries, reports, inventory movements, and audit history.
7. Restore service with an explicit approval, then monitor errors and sync failures.
8. Document impact, RPO/RTO result, root cause, corrective actions, and the next restore drill.

## Restore drill checklist

- [ ] Export path and checksum recorded.
- [ ] Firestore import completed in an isolated project.
- [ ] Storage objects sampled and downloaded successfully.
- [ ] Auth users and claims reconciled.
- [ ] Rules and indexes deployed.
- [ ] Backend tests and rules tests passed.
- [ ] Flutter login, task read, report draft, attachment, and sync smoke tests passed.
- [ ] Tenant A cannot read Tenant B after restore.
- [ ] Restore duration and data-loss window recorded.
