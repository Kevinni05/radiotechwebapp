# Esercizio e consegna RadioTech

Aggiornamento 12 ottobre 2026. Queste procedure descrivono il codice disponibile; non attestano un pilota, SLA, conformità o validazione commerciale già completati.

## Dashboard e proiezioni

Il lettore di dashboard è disabilitato per default: `RADIOTECH_DASHBOARD_PROJECTIONS_ENABLED=false`. Le sei funzioni in functions/ riconciliano lo stato corrente di ogni documento e il ledger precedente nello stesso commit. Eventi duplicati o fuori ordine non sommano nuovamente i contatori; il trasferimento di tenant sottrae e aggiunge correttamente. Le attività storiche non sono lette per il calcolo SLA, che consulta la sottocollezione activeTasks.

1. Verificare autorizzazioni, piano/billing e disponibilità Cloud Functions sul progetto esistente gestionale-radio. Il tentativo locale di functions:list del 12 ottobre è fallito; non è un deploy riuscito e non prova il piano di fatturazione.
2. Eseguire `npm ci --prefix functions` e `npm run test:firestore` con demo-radiotech.
3. Distribuire `firebase deploy --only functions:radiotech-projections --project gestionale-radio` soltanto dopo aver verificato i prerequisiti sul progetto. I trigger sono europe-west1; la base esistente è eur3. Non è prevista alcuna ricreazione del database.
4. Per ogni tenant autorizzato eseguire `FIREBASE_PROJECT_ID=gestionale-radio node functions/backfill.cjs TENANT` con identità server autorizzata. Il backfill marca ready=false prima di iniziare e ready=true soltanto al completamento; è ripetibile.
5. Confrontare conteggi e tenant con la query legacy e registrare letture/tempo/errori su dataset sintetico isolato. Abilitare il lettore solo dopo la verifica. Disabilitare il flag per rollback: gli archivi originali rimangono autorevoli.

Modello di letture: la dashboard proiettata legge un documento di contatori e le sole attività attive. La riduzione del 70% resta un target da misurare sul carico concordato: non è una misura della produzione. Le mutazioni generano letture/scritture aggiuntive nel ledger; misurare anche queste nel budget.

## Allegati e migrazione

HTTP upload accetta PDF, JPEG, PNG e WebP con firma binaria ed estensione coerenti. Limite 10 MB/file, 50 allegati; per riferimenti opachi persistenti il report verifica anche il limite aggregato di 50 MB e registra referencedBy nella propria transazione. I vecchi riferimenti Firebase sono mantenuti per compatibilità e richiedono una verifica separata della loro dimensione.

`/api/v1/files/ID/content` autorizza tenant e proprietario prima della lettura, verifica digest e dimensione prima di inviare gli header, e trasferisce con buffer limitato. Il vecchio endpoint JSON rimane disponibile per client precedenti. Il client nuovo usa il fallback solo su 404. Le copie temporanee vengono rimosse alla conclusione dello stream o in caso di integrità errata.

Configurare esplicitamente FIREBASE_PROJECT_ID, FIREBASE_STORAGE_BUCKET e un'identità server autorizzata. Eseguire `./gradlew attachmentMigration -PmigrationMode=dry-run -PmigrationTenant=TENANT -PmigrationLimit=25`, poi apply. Per proseguire usare -PmigrationAfter con il nextAfterId stampato; il checkpoint viene scritto dopo ogni file verificato. Il trasferimento conserva ID, riferimenti e chunk originali, controlla i byte su Storage prima di committare i metadati. `rollback` ripristina il backend Firestore solo dopo aver verificato la copia legacy. Non eliminare i chunk durante la migrazione.

Pulizia: `node functions/orphans.cjs dry-run TENANT [afterId]`, poi apply soltanto per gli upload individuati e verificati. Batch massimo 100, default 25. La finestra minima è 30 giorni. Sono eleggibili esclusivamente metadati lifecycleVersion=1 prodotti dal server che collega i report atomicamente; upload legacy, recenti e referencedBy sono conservati. DELETING impedisce la creazione di un report durante la cancellazione; un'interruzione lascia metadati recuperabili e ripetibili. I report rimossi continuano a trattenere gli allegati: la retention documentale va concordata prima di eliminarli. Nessuna pulizia distruttiva è stata eseguita sul progetto reale.

## Manuale responsabile

Accedere con account aziendale, verificare l'azienda e l'ambiente indicati nella barra. Registrare impianti e operatori, assegnare l'incarico al tecnico e verificare scadenza/posizione. Monitorare stati e segnalazioni; accettazione push non equivale a lettura. Per revisionare un report aprire PDF, checklist, misure e materiali; approvare una volta oppure rifiutare con motivazione. La rimozione conserva documento/audit e non annulla automaticamente i consumi. Controllare magazzino e storico prima di una rettifica. Gestire ruoli e revoche soltanto con capacità amministrative; ENGINEER gestisce operazioni, senza amministrare gli account.

## Manuale tecnico

Verificare account, azienda, servizio e coda prima del turno. Aprire l'incarico, controllare impianto/checklist, scansionare QR e consentire posizione quando richiesta. Con GPS insufficiente usare la procedura di retry: non dichiarare un check-in riuscito senza conferma. Compilare misure con unità, checklist, foto e materiali; controllare preview e inviare. Bozza e coda non sono una ricevuta della centrale. Conservare la ricevuta server prima di considerare completato l'invio. Una segnalazione urgente offline rimane da inviare; non affidarsi alla sola push per un'emergenza. Riprendere l'app quando torna la rete: il processo terminato non garantisce sync in background. Non disinstallare con bozze o operazioni ancora in coda.

## Consegna, supporto e dati

Prima della vendita assegnare nominativamente proprietario GitHub/Firebase/Render/dominio, responsabile firma Android, custode della chiave backup e referente supporto. Conservare accessi e copie di recupero separatamente; trasferire gli account tramite i provider, senza password nei documenti. Registrare URL, regione, revisioni, buildNumber, digest, certificato, backup e ultimo restore. Provare il manuale con un nuovo utente.

Il registro dati comprende identità/claims, assegnazioni, posizione e accuratezza, foto, checklist, misure, materiali, firma grafica, audit e notifiche. Per ogni categoria indicare finalità, ruoli autorizzati, retention concordata, esportazione/rimozione e subfornitori. La firma grafica e l'hash non attestano una firma qualificata. Contratto, licenza/cessione, privacy, titolarità e retention devono essere definiti dai responsabili competenti prima della commercializzazione.

Registrare incidenti con requestId, ora, tenant e versione, senza token/foto/documenti nei ticket. Distinguere rete, autenticazione, quota e conflitto. Una riparazione non deve ripetere un consumo o cambiare operationId. Ripristinare prima su ambiente isolato e confrontare dati, claims e byte degli allegati; documentare RPO/RTO misurati e riconnessione client.

## Capacità, budget e pilota

| Scenario mensile sintetico | Utenti | Asset | Report nuovi | Foto/report | Foto compressa | Nuovi byte foto |
|---|---:|---:|---:|---:|---:|---:|
| Piccolo | 10 | 100 | 200 | 3 | 500 KB | 300 MB |
| Riferimento | 50 | 1.000 | 1.000 | 5 | 500 KB | 2,5 GB |
| Espanso | 200 | 5.000 | 5.000 | 5 | 500 KB | 12,5 GB |

Aggiungere PDF, retention storica, backup, download e invii; i valori sono ipotesi di capacità, non misure o fatture. Per ogni scenario calcolare costo server + letture/scritture/query/trigger + storage/egress + backup + autenticazione e dominio ai prezzi effettivi del contratto/regioni. Inserire budget/alert nel progetto, verificare il destinatario dell'allarme e provare il ripristino. Non aumentare capacità o billing senza un budget definito.

Pilota: almeno due settimane o campione equivalente concordato. Registrare per giorno flussi eseguiti, rete/dispositivo, crash, p95/p99, byte/memoria, duplicati, tempi sync, errori e feedback. Testare assegnazione→QR→check-in→report→approvazione→magazzino, timeout dopo commit, chiusura forzata, logout A/login B, sospensione, push parziali, upgrade e restore. Il telefono fisico più emulatore è l'assetto scelto dall'utente; resta da completare il collaudo funzionale completo, oltre al test nativo dell'archivio già passato.
