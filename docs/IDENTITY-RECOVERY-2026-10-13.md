# Recupero dell'autorità d'identità

Il backup operativo di gestionale-radio non comprende il registro separato radiotech-id-1051055442339. Il recupero completo richiede entrambi: dati/allegati/utenti applicativi e registro delle approvazioni d'identità. Non concedere accesso al registro alla pipeline web o al runtime per unificare i backup.

## Strumento amministrativo

`identity-service/src/registry-backup.js` si esegue nell'ambiente amministrativo del proprietario. Esporta collezioni e sottocollezioni in transazione di sola lettura, preserva timestamp, byte, riferimenti e tipi; cifra con AES-256-GCM e nonce casuale. Verifica progetto, percorsi univoci e integrità prima del restore. Una chiave indipendente da quella della pipeline web protegge il registro. Archivio, chiave e copie recuperate devono restare fuori dal repository.

Da questa worktree, dopo `npm ci --prefix identity-service`, usare percorsi privati reali e credenziali amministrative già configurate; nessun valore dei segreti nella riga di comando:

```text
node identity-service/src/registry-backup.js export --registry-project radiotech-id-1051055442339 --archive PERCORSO_PRIVATO_ARCHIVIO --key-file PERCORSO_CHIAVE_32_BYTE --secret-file PERCORSO_PRIVATO_APP_SECRET
node identity-service/src/registry-backup.js verify --registry-project radiotech-id-1051055442339 --archive PERCORSO_PRIVATO_ARCHIVIO --key-file PERCORSO_CHIAVE_32_BYTE
```

Export/verify non cambiano dati del registro. L'archivio non sovrascrive copie precedenti; errori o tipi non supportati interrompono l'export. Congelare modifiche amministrative/schema durante l'esportazione. Registrare creazione, digest, destinazione e custodio senza pubblicare contenuti o chiavi.

## Prova isolata

Con Firestore emulator sul loopback, eseguire `restore-emulator` aggiungendo `--recovery-directory DIRECTORY_PRIVATA`. Il progetto destinazione deve essere demo-radiotech-identity-restore; host cloud, progetto produzione e database non vuoto sono rifiutati. Ogni documento/tipo è confrontato con il contenuto autenticato; i byte del segreto recuperato sono verificati.

Evidenza del 13 ottobre 2026: export reale e verifica riusciti, **90 documenti e un segreto applicativo**; archive SHA256 `a327cc01405e46230c5d51f35e3cf9fc213270842fed9f51b43e3f68ee97363c`, creazione 15:03:02 UTC. Restore e confronto riusciti nell'emulatore isolato, nessuna scrittura sul registro reale. Suite dell'autorità: 28 test riusciti, inclusi alterazione, chiave/progetto errati, percorsi duplicati e rifiuto restore in produzione.

Archivio, chiave separata, segreto recuperato e log sono privati in .radiotech/identity. Non caricarli nei normali artefatti CI, nella distribuzione APK o nei ticket. La copia locale non costituisce da sola un backup offsite.

## Apertura dopo un disastro reale

Il tool rifiuta il restore cloud: preparare il ripristino concreto e farlo eseguire dall'amministratore autorizzato. Prima di riaprire, riconciliare registro, Auth, audit e userLocks; controllare revoche successive alla copia e invalidare nonce/challenge/sessioni recuperate. Non eliminare blocchi per forzare una concessione incompleta. Verificare Secret Manager, origine, IAM e fattore del proprietario. Distribuire l'autorità dall'ambiente amministrativo indipendente.

L'archivio non esporta chiavi private delle passkey, credenziali Google o seed MFA Firebase. Provare il recupero dei fattori con la procedura del proprietario: sospensione, revoca sessioni, verifica email, nuovo MFA e nuova approvazione. Un reset password non sostituisce questa verifica.

Restano custode/sostituto, destinazione offsite amministrativa separata, copia della chiave in una destinazione distinta e RPO/RTO end-to-end. Sono gate DATA-04 nel [registro Enterprise](ENTERPRISE-PLAN-2026-10-13.md).
