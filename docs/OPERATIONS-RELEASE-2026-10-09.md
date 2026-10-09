# Archivi, monitoraggio e distribuzione Android — 9 ottobre 2026

## Stato dell'archivio

Firestore rimane il backend degli allegati su Render. Nessun bucket è stato creato, nessun piano di fatturazione è stato modificato e nessun report è stato cancellato. I 9 allegati occupano 1.561.029 byte in 13 blocchi; gli archivi contengono 8 incarichi e 3 report. Il precedente problema di visibilità e cancellazione degli operatori è corretto nel commit `2576473`.

Le liste web caricano 50 elementi per pagina; lo storico mobile ne carica 30 per ciascuna sorgente. Gli endpoint `/tasks/page`, `/reports/page` e le rispettive varianti `/operator/me/...` utilizzano cursori, ordine data/ID e filtri azienda/operatore sul server. La ricerca nelle schermate paginata riguarda gli elementi caricati. Le vecchie API rimangono disponibili per agenda e integrazioni e possono ancora leggere intere liste della singola azienda: il successivo intervento di scala riguarda quelle viste e la ricerca nell'intero archivio.

Dopo il backup e la prova di ripristino, 11 documenti sono stati aggiornati con soli metadati di indicizzazione. I contenuti originali, gli allegati e i campi usati per hash e firme non sono stati riscritti. Gli indici di `firestore.indexes.json`, compresa l'esclusione dei blocchi binari dagli indici, sono stati applicati al progetto esistente. `archiveMigration` è idempotente; una successiva esecuzione non propone aggiornamenti.

## Monitoraggio e allarmi

Il pannello **Sistema** interroga `/api/v1/operations/health`, protetto da autorizzazione analytics e azienda. Mostra dimensione e blocchi degli allegati, report in attesa, ultimo backup e prova di ripristino, metriche effettive Google Cloud Monitoring. L'account di servizio esistente ha ricevuto esclusivamente il ruolo aggiuntivo `roles/monitoring.viewer`; gli altri permessi sono stati preservati. L'API Monitoring era già abilitata. I valori non disponibili rimangono sconosciuti.

Le metriche delle operazioni sono aggregate dalla mezzanotte di Los Angeles, come il giorno delle quote Firestore, e memorizzate per 10 minuti. I dati sono riferiti al database condiviso e possono arrivare in ritardo. Le dimensioni degli allegati misurate direttamente non coincidono necessariamente con il valore aggregato storage di Monitoring. La console di fatturazione è la fonte finale per i costi: [quote ufficiali](https://firebase.google.com/docs/firestore/quotas), [monitoraggio ufficiale](https://firebase.google.com/docs/firestore/monitor-usage).

Soglie predefinite: 40.000 letture/giorno, 16.000 scritture/giorno, 16.000 eliminazioni/giorno, 0,8 GiB storage; avviso passaggio agli oggetti a 64 MiB di allegati. Report `SUBMITTED` oltre 24 ore, backup esterno oltre 36 ore e ripristino oltre 35 giorni producono allarmi. Quote esaurite sono registrate nel processo server per un'ora e riconosciute dall'endpoint salute Firebase.

Il workflow `availability-monitor.yml` controlla API/Firestore ogni 15 minuti; il controllo aggregato operativo avviene ogni ora al minuto 7. Errori e superamento delle soglie fanno fallire il relativo job. Abilitare le notifiche dei workflow GitHub nell'account per ricevere gli avvisi; questa modifica non invia messaggi a email o chat esterne. Le pianificazioni GitHub possono subire ritardi e, nei repository pubblici inattivi, essere sospese: non costituiscono uno SLA. Le aziende monitorate sono configurate nel secret `RADIOTECH_MONITOR_TENANT_IDS`; aggiornare la lista quando si aggiungono aziende.

Le soglie Render si possono configurare con `RADIOTECH_OPERATIONS_DAILY_READ_WARNING`, `RADIOTECH_OPERATIONS_DAILY_WRITE_WARNING`, `RADIOTECH_OPERATIONS_DAILY_DELETE_WARNING`, `RADIOTECH_OPERATIONS_STORAGE_WARNING_BYTES`, `RADIOTECH_OPERATIONS_OBJECT_STORAGE_WARNING_BYTES` e `RADIOTECH_OPERATIONS_PENDING_REPORT_HOURS`. Per applicare le stesse soglie al controllo orario impostarle anche nell'ambiente del relativo job; in assenza di override entrambi usano i default documentati.

## Backup esterni e ripristino

Il workflow `cloud-backup.yml`, giornaliero alle **03:13 UTC** e avviabile manualmente, esporta Firestore, identità Auth e allegati Firestore, cifra l'archivio con AES-256-GCM, lo autentica e lo ripristina negli emulatori `demo-radiotech-backup`. Confronta i documenti restaurati, le identità disabilitate e il contenuto/SHA-256 degli allegati prima del caricamento esterno su GitHub Actions. Registra il successo nel database soltanto dopo l'upload riuscito. Il workflow non esegue un ripristino sul progetto di produzione.

I secret GitHub `FIREBASE_BACKUP_SERVICE_ACCOUNT`, `FIREBASE_BACKUP_KEY_BASE64` e `RADIOTECH_MONITOR_TENANT_IDS` sono configurati. La chiave locale è conservata fuori dal repository e da OneDrive in `%USERPROFILE%/.radiotech/backups/cloud-backup.key`; mantenerne una copia separata e protetta. La conservazione degli artefatti GitHub è di 90 giorni: per conservazione più lunga scegliere uno storage esterno con retention appropriata.

Per riconfigurare i secret da un altro checkout: installare PyNaCl in un ambiente Python, impostare `FIREBASE_SERVICE_ACCOUNT_PATH` al file credenziale esistente e `RADIOTECH_MONITOR_TENANT_IDS` a un array JSON degli ID azienda; eseguire `scripts/configure-cloud-ci.py` con una credenziale GitHub già memorizzata dal gestore Git. Ripristinare prima le chiavi esistenti nelle cartelle private indicate: lo script non sostituisce chiavi presenti. `scripts/configure-monitoring.cjs` richiede un login Firebase CLI locale del proprietario del progetto e aggiunge solo Monitoring Viewer.

La prova locale precedente alla migrazione ha ripristinato e confrontato **155 documenti, 14 identità e 9 allegati**. Gli account ripristinati rimangono disabilitati. Il backup è una lettura applicativa, non un punto nel tempo transazionale dell'intero database; per ripristini di produzione e archivi molto grandi è opportuno pianificare una finestra senza scritture o valutare un servizio di backup gestito.

La prima [esecuzione esterna verificata](https://github.com/Kevinni05/radiotechwebapp/actions/runs/37866055852) ha completato export cifrato, ripristino/confronto e upload alle 00:43 UTC del 9 ottobre, con gli stessi 155 documenti, 14 identità e 9 allegati. Lo stato registrato nel database è `OK`. Il [controllo automatico API e soglie](https://github.com/Kevinni05/radiotechwebapp/actions/runs/37866315544) è passato; il pannello protetto è stato verificato via HTTPS su Render con un account amministrativo esistente, senza modificare ruoli o autorizzazioni.

## Predisposizione storage per oggetti

Configurazione attiva Render: `RADIOTECH_ATTACHMENT_BACKEND=FIRESTORE`. Non usare il nome di un bucket presunto: quello storico `gestionale-radio.firebasestorage.app` non esisteva.

Quando sarà disponibile un bucket verificato, impostare `FIREBASE_STORAGE_BUCKET=<nome-reale>` e `RADIOTECH_ATTACHMENT_BACKEND=FIREBASE_STORAGE` dopo averne verificato costi, permessi, upload/download e backup. I nuovi upload saranno oggetti privati, con metadati azienda/autore/hash in Firestore. I vecchi riferimenti Firestore continueranno a essere leggibili senza migrazione o riscrittura dei report; questa compatibilità è coperta da test con cambio di backend senza bucket.

Prima del passaggio effettivo estendere la prova di ripristino con un bucket isolato: la prova attuale rifiuta un archivio contenente oggetti cloud, evitando di dichiarare verificato un ripristino non eseguito. Il presente intervento prepara la configurazione e conserva Firestore, come richiesto.

## Android e canale di aggiornamento

La versione mobile è `1.5.0+2021`, con indicatore di disponibilità API effettiva e controlli aggiornamento. La nuova chiave RSA 4096 di produzione è conservata in `%USERPROFILE%/.radiotech/signing/radiotech-production.p12`; password e configurazione sono in `key.properties` nella stessa cartella, fuori da Git/OneDrive. SHA-1 e SHA-256 sono registrati nell'app Firebase Android esistente. Conservare una copia protetta della chiave: perderla impedirebbe gli aggiornamenti futuri firmati con questa identità.

Certificato SHA-256 pubblico: `73005990b6bad93b1bb3383121f6dcc2293b3e5e3a60e1ae7eaf09a7d97c4fa4`.

Il workflow mobile `production-release.yml` produce APK/AAB con la chiave configurata, verifica il certificato e cifra i simboli con una chiave distinta (`ANDROID_SYMBOLS_KEY_BASE64`). La pubblicazione è disabilitata per default. La chiave dei simboli è `%USERPROFILE%/.radiotech/signing/symbols.key`; il formato cifrato contiene nonce di 12 byte, tag GCM di 16 byte e ciphertext di un archivio tar.gz.

Per pubblicare una versione, avviare il workflow con `publish=true`; poi eseguire `scripts/sync-mobile-release.ps1 -Tag v1.5.0-2021 -ApkSigner <percorso-apksigner>` nel repository web. Lo script controlla release pubblica, APK, checksum e certificato prima di attivare il manifest; commit/push del manifest abilita il canale sul server. L'APK scaricabile deve essere esattamente quello del manifest, perché build locali e CI possono avere checksum diversi.

Il manifest iniziale rimane `available:false` fino a una release pubblicata e verificata. L'app accetta solo release dal repository previsto e con lo stesso certificato installato. La nuova firma non aggiorna direttamente l'APK di test precedente: il telefono non viene disinstallato o azzerato. Verificare/salvare eventuali report locali prima della prima installazione di produzione. Android è verificato; iOS richiede macOS e una verifica separata.

## Verifiche applicative

Analisi Flutter senza problemi e 119 test mobile superati dopo l'integrazione del commit remoto `a7884b0`. Suite backend: 143 test locali eseguiti senza errori; test con emulatori eseguiti separatamente. Le verifiche browser e regole/emulatori sono passate anche nella CI. La verifica HTTPS su Render ha confrontato le liste classiche e paginata dell'operatore (4 incarichi, 3 report), il conteggio aggregato e gli SHA-256 di tutti i 9 allegati. Il manifest predisposto risponde pubblicamente e non presenta release non pubblicate.
