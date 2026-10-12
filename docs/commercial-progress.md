# RadioTech — registro di consolidamento commerciale
Aggiornato il 12 ottobre 2026. Piano di riferimento: [piano completo](commercial-consolidation-plan.md).
Repository mobile: [gestionale_radio](https://github.com/Kevinni05/gestionale_radio).

## Stato della release
**Gate di vendita NON superato.** Le modifiche riportate qui sono circoscritte; nessuna CI verde certifica l'intero piano. "Parziale" significa che almeno un criterio resta aperto. "Da validare" significa che la base esiste, ma non è stata dimostrata l'accettazione del piano.

## Modifiche ed evidenze
- Backup: [PR13](https://github.com/Kevinni05/radiotechwebapp/pull/13), [export e restore reali riusciti](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089481829). Archivio cifrato, 137 documenti, 5 identità/claims, 12 allegati Firestore. Restore su emulatori con account disabilitati; non prova recupero di password/provider/MFA in produzione. Storage non configurato: nessun oggetto storage verificato.
  - testedAt: 2026-10-10T21:57:54.530202343Z
  - SHA256 archivio: d56355f4d36c061e3beda8901ba46adb1c09c52da3317b84033986381d248109
  - Retention artefatto: 90 giorni; non sostituisce copia e chiave sotto ownership del gestore.
- Monitor: [run riuscito](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38090130601). La prova del backup deriva dal restore sopra, non dal solo health.
- ENGINEER: [web PR14](https://github.com/Kevinni05/radiotechwebapp/pull/14), [mobile PR20](https://github.com/Kevinni05/gestionale_radio/pull/20). Accesso gestionale coerente con NETWORK_MANAGER, senza amministrazione account o identità operatore implicita. Regole repository testate; deploy delle regole effettive ancora da verificare.
- Urgente/report: [mobile PR19](https://github.com/Kevinni05/gestionale_radio/pull/19), [CI riuscita](https://github.com/Kevinni05/gestionale_radio/actions/runs/38089588769). Chiave e payload persistiti prima della rete; ricevuta distinta dal report; retry riusa la stessa operazione; bozza corrotta non viene trattata come vuota. Test di tre retry con risposta persa; prova reale di riavvio dispositivo ancora aperta.
- Offline: PR19 impedisce al pannello di mostrare operazioni senza identità o appartenenti a utente/tenant diversi; test negativi espliciti.
- Refresh: [PR15](https://github.com/Kevinni05/radiotechwebapp/pull/15), [CI riuscita](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089621209). Solo vista attiva, richieste concorrenti deduplicate, sospensione tab nascosta e protezione edit/focus.
- Rate limit: [PR16](https://github.com/Kevinni05/radiotechwebapp/pull/16), [CI riuscita](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089626012). Alias /api e /api/v1 e riferimenti verifica report condividono budget. Replica/proxy reale non provati.
- Audit report/materiali: [PR17](https://github.com/Kevinni05/radiotechwebapp/pull/17), [cinque job CI riusciti](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089758505). Eventi nello stesso batch/transaction della mutazione, identità esplicita, replay senza eventi/consumi doppi; consumo fallito senza audit di successo.
- Notifiche: [PR18](https://github.com/Kevinni05/radiotechwebapp/pull/18), [cinque job CI riusciti](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089933072). Ricevute per token con hash, retry solo falliti, PARTIAL, lease, backoff/jitter, DEAD_LETTER; rimozione effettiva UNREGISTERED. I vecchi invii immediati non sono tutti migrati. FCM può accettare prima di un errore di persistenza: resta semantica almeno una volta.
- Audit identità: [PR19](https://github.com/Kevinni05/radiotechwebapp/pull/19). Intento durevole prima di Firebase Auth, conferma sincrona, PENDING dopo interruzione. [CI completa riuscita con nuovi test realmente selezionati](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38090853049). La PR30 aggiunge riconciliazione con lease e backoff; il monitor operativo segnala i PENDING. Non ripristina claims superate: conferma soltanto il risultato che coincide con l’intento originale.
- Updater personalizzato: [mobile PR21](https://github.com/Kevinni05/gestionale_radio/pull/21). Identità installata e nome app nativi, stesso contratto filename del workflow, rifiuto app diversa e firma incompatibile. [Analisi, test e compilazione Android riusciti](https://github.com/Kevinni05/gestionale_radio/actions/runs/38090371861). La verifica dei byte è stata integrata con la PR24 mobile dopo CI completa 38129987798, senza equipararla a un upgrade su dispositivo.

## Backlog unico — backend
| ID | Stato | Criteri ancora aperti |
|---|---|---|
| B01 | Parziale, recovery dei dati attuali provato | Recovery produzione completo di identità, chiave separata recuperabile, owner, misure RPO/RTO, storage e riconnessione client |
| B02 | Parziale | Matrice completa ruolo×endpoint×tenant e riferimenti indiretti; deploy/verifica regole Firebase; revoca e sospensione su staging |
| B03 | Parziale | PR28 rende task/GPS/audit atomici; PR30 riconcilia APPROVAL_PENDING e consumo già committato. Restano crash dopo ogni fase e riavvio reale integrato |
| B04 | Parziale | Tutti i cambi identità/azioni critiche; copertura completa, policy retention e accesso definiti; riconciliazione PENDING identità implementata nella PR30 |
| B05 | Parziale | E2E tre retry e riavvio su Android, un solo segnale e un solo report; integrità ricevute/bozze dopo upgrade |
| B06 | Parziale | Cache tenant-scoped PR26 e timestamp PR29; restano baseline, aggregazioni/proiezioni, invalidazione completa, viewport, prova riduzione letture ≥70% |
| B07 | Parziale | PR31 limita allocazione del body e concorrenza prima della lettura, rimuove il lock globale. Restano storage, migrazione dry-run/checkpoint/digest/rollback, streaming download, carico, cleanup e retention |
| B08 | Parziale | Migrare invii immediati, allarme dead letter, cleanup retry, deduplica/deep link/logout e device freddo |
| B09 | Parziale | DTO/errori/deadline/concorrenza, proxy e repliche, timeout/quote e vecchio mobile integrati |
| B10 | Parziale | Snapshot tecnico e identità immutabili PR25; restano coerenza PDF mobile/checklist/materiali e prova integrata con rinomina dati |

## Backlog unico — web
| ID | Stato | Criteri ancora aperti |
|---|---|---|
| W01 | Da validare | Inventario e consolidamento componenti/token e varianti condivise |
| W02 | Parziale | PR32 estrae otto template feature e il controller esterno, con CI verde. Restano controller per feature e consolidamento componenti |
| W03 | Da validare | Tastiera, distinzione staging/produzione e shell con dati reali |
| W04 | Parziale | Significato stato asset e timestamp server implementati/testati PR29; restano drill-down e dati reali |
| W05 | Da validare | Ordine/pannelli e contenuti realistici su desktop/tablet/mobile |
| W06 | Da validare | PDF/materiali, tastiera, rimozione motivata e operazioni concorrenti |
| W07 | Da validare | Simbolo/legenda accessibili e selezione bidirezionale reale |
| W08 | Da validare | Nomi lunghi, note, touch/tastiera e overflow |
| W09 | Da validare | Cursor e filtri server per ogni tabella, ritorno dal dettaglio e stabilità pagina |
| W10 | Implementato e testato in CI | Verifica autenticata con dati reali e dipendenze di produzione |
| W11 | Da validare | Tastiera, focus/dialog, contrasto, zoom 200%, lettura errori |
| W12 | Parziale | Dialoghi guidati PR23; restano configurazione effettiva e prova MFA/reauth/recovery su staging |
| W13 | Parziale | Sessioni gestite da Firebase SDK PR27, eliminati token applicativi; restano logout/multi-tab autenticati su staging |
| W14 | P2 aperto | Ricerca/filtri salvati autorizzati e utilità cliente |

## Backlog unico — mobile
| ID | Stato | Criteri ancora aperti |
|---|---|---|
| M01 | Da validare | Asset/dati/note/incarico nella prima schermata reale |
| M02 | Da validare | Checklist diretta unica e stessa versione report/PDF |
| M03 | Da validare | GPS/accuratezza/distanza/retry e deroga autorizzata su device |
| M04 | Parziale | Invio urgente recuperabile; completare/validare tutte le fasi upload/report/preview/ricevuta |
| M05 | Parziale | Archivio Android SQLite/AES-GCM, Keystore, cancellazioni autenticate, migrazione validata e foto cifrate PR24 (PR23 incorporata); restano prova upgrade/riavvio e Keystore su dispositivi reali, bonifica dei dati legacy non visitati |
| M06 | Da validare | Avvio/resume/rete, conflitti, capienza e comportamento background dichiarato |
| M07 | Aperto | Feature/repository/stato Riverpod, separazione widget e operazioni |
| M08 | Da validare | PDF/foto realistici, tempi/memoria su dispositivo medio |
| M09 | Da validare | Schermi piccoli, sole, textScale 2, TalkBack e tastiera |
| M10 | Parziale | Server retry parziale; deduplica/deep link/foreground/background/logout su due dispositivi |
| M11 | Parziale | Updater personalizzato PR21 e download con digest/pacchetto/firma PR24; restano canale commerciale, versione minima, upgrade e rollback reali |
| M12 | Bloccato dalla mancanza di device/sessione | Due Android reali, QR/GPS/foto/firma/FCM/App Check e rete degradata |
| M13 | P2 aperto | Cache pack, checklist tipo impianto e freschezza offline |

## Registro delle prove integrate
1. Due tenant/ruoli: alcune prove emulatori e browser presenti; matrice completa non chiusa.
2. Web→mobile→QR→check-in→report/allegati→approvazione→magazzino: E2E autenticata reale non eseguita.
3. Rete persa/upload/timeout dopo commit/chiusura forzata: simulazioni mirate presenti, device reale aperto.
4. Urgente con risposta persa: test PR19 riuscito; device/restart e report end-to-end aperti.
5. Approvazione/rimozione concorrenti: acceptance completa aperta.
6. Logout A/login B: pannello e scope testati; coda/bozze/device reale aperti.
7. Revoca/sospensione/cambio ruolo: audit intenzione testato; E2E reale aperta.
8. FCM parziale/token scaduti: test emulatori riuscito nella CI 38090853049; FCM e avvio a freddo reali aperti.
9. Upgrade firmato conserva coda/bozze, firma errata respinta: aperto.
10. Restore dati/claims/allegati attuali isolato riuscito; ripristino identità produzione e client aperti.
11. Meteo/quota: test esistenti non equivalgono alla prova integrata richiesta; aperto.
12. Render, prova storica del 10 ottobre: commit 9d54b44e6d21f477a047fff18a17e948ecf7f6ee confermato live dal deploy dep-db5bm08u01pc73ehm0lg, terminato alle 22:27:28 UTC. È una verifica della revisione del deploy, non un'attestazione del digest del JAR. La successiva revisione dei metadati di release richiede nuova verifica live.

## Gate esterni e consegna
Non esporre segreti in issue, log, PR o documentazione. Render è collegato e il servizio esistente è stato identificato dal repository e dall'URL. L'integrazione Firebase non risulta ancora installata. Servono accessi autorizzati al progetto Firebase e uno staging isolato prima di applicare migrazioni/regole o fare prove con carico.
- Confermare/deployare regole e indici e verificare IAM, App Check enforcement, billing/quote/alert, ownership e backup chiavi.
- Render reale: autoDeployTrigger=commit e healthCheckPath vuoto, mentre render.yaml richiede checksPass e /actuator/health/readiness. Allineare queste impostazioni dalla Dashboard (la connessione corrente non espone un'operazione di modifica dei parametri del servizio), quindi verificare nuovo deploy e smoke autenticato. Identificazione dell'artefatto ancora aperta.
- Eseguire collaudo Android su almeno due dispositivi e prova upgrade con dati locali.
- Misurare carico concordato (50 utenti/1.000 asset/10.000 report), p95/p99, memoria, letture, sincronizzazione, crash e costi; nessun target è già certificato.
- Provisioning secondo ambiente, dati demo sintetici, export/reimport byte+digest, manuali verificati con utente nuovo, simulazione supporto.
- Scegliere licenza/cessione, ambiente/canale Android, responsabilità e owner supporto; revisione privacy/GPS/foto/firme/retention e licenze terze parti.
- Tre scenari economici, budget/alert e handover account/firma/backup.
- Pilota almeno due settimane o campione equivalente esplicitamente concordato; registro incidenti/feedback.

## Verifiche conclusive delle correzioni implementate
- [CI web combinata 38091508753](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38091508753): cinque job riusciti, incluse regole/emulatori, browser, JAR riproducibile e container. La selezione dei test ora comprende automaticamente *EmulatorTest, conservando i test con nomi non standard.
- [Metadati release PR21](https://github.com/Kevinni05/radiotechwebapp/pull/21): health espone apiVersion, sourceRevision e revisionSource. Accetta solo SHA completo; Render ha precedenza; assenza/valore invalido restituisce UNKNOWN/UNVERIFIED senza mostrare input. Non è attestazione dei byte dell'artefatto.
- [APK firmato distribuito 38089950365](https://github.com/Kevinni05/gestionale_radio/actions/runs/38089950365): invio effettivo ai tester Firebase riuscito per segnale urgente, isolamento pannello offline e ENGINEER. La build successiva con updater è stata firmata e distribuita ai tester con successo in [38091027745](https://github.com/Kevinni05/gestionale_radio/actions/runs/38091027745); non confondere App Distribution con il canale commerciale.
- Render, osservazione del 10 ottobre alle 22:31 UTC: nessun log ERROR restituito dalla query da 22:27:28; memoria campionata ~294 MB su limite 512 MB. Serie HTTP p95 e conteggio richieste vuote: prestazioni e capacità NON misurate nel carico di riferimento.
- Connessione Render verificata sul workspace con unico servizio radiotechwebapp e repository Kevinni05/radiotechwebapp. Nessun nuovo servizio, cambio piano, bucket o fatturazione effettuato.

## Consolidamento dell’11 ottobre 2026

- Web PR28 e PR29 integrate: transizioni GPS/task con audit atomico e KPI di attività asset distinti dall’uptime, con timestamp del server.
- Web PR30 integrata dopo cinque job CI riusciti ([38128489615](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38128489615)): recupero approvazione interrotta dopo consumo materiali già committato, anche con report rimosso; conservazione del revisore originale e dei consumi senza duplicazioni. Riconciliazione degli intenti Firebase Auth solo se tenant e claims correnti coincidono, senza applicare vecchi ruoli.
- Web PR31 integrata dopo CI riuscita ([38128733298](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38128733298)): permit prima della lettura del body, limite di 10 MB e upload concorrenti limitati; rimosso il synchronized globale. Questa misura non completa la migrazione storage o lo streaming dei download.
- Web PR32 integrata dopo cinque job CI riusciti ([38129049982](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38129049982)): otto template feature, shell da circa 3.100 a 446 righe e controller esterno. I controller per feature restano da estrarre.
- Web PR33 integrata alla revisione `a1fe96f752481be07fb0e5b172f9b0741af1eb09` dopo tutti e sette i job CI riusciti ([38132239351](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38132239351)): 112 test Chromium, 112 Firefox e 112 WebKit, senza skip, oltre a test backend, regole/emulatori Firebase, riproducibilità JAR e container. Corretta la race del focus tastiera nel menu mobile WebKit; consentito il fallback PNG del canvas quando WebP non è supportato. Artefatti di screenshot/trace separati per browser. Queste prove non sostituiscono contenuti autenticati reali, dispositivi, zoom/TalkBack o carico produzione.
- Mobile PR24 integrata alla revisione `2a5bac399f6790187b0b57a657dc3cf3c9dd64c9`, dopo analisi Flutter, test Dart, compilazione APK e cinque test nativi riusciti ([38129987798](https://github.com/Kevinni05/gestionale_radio/actions/runs/38129987798)). SQLite/AES-GCM/Keystore per coda, bozze, ricevute e idempotenza; foto operative cifrate; tombstone autenticate; protezione da chiave sostituita e cancellazione di file estranei. Updater con download limitato, SHA-256 effettivo e verifica del pacchetto/certificato prima dell’installatore. PR22 e PR23 chiuse come alternative/incorporate; PR17 chiusa perché le modifiche erano già presenti in main.
- Mobile main: [CI e distribuzione firmata 38130872668](https://github.com/Kevinni05/gestionale_radio/actions/runs/38130872668) completate con successo. APK `1.5.1 (10074)`, 86,9 MB; upload della nuova release confermato alle 10:36:05 UTC e invio a tester/gruppi confermato alle 10:36:06 UTC. [Release per i tester autorizzati](https://appdistribution.firebase.google.com/testerapps/1:1051055442339:android:f495a6326f7a7465fb7216/releases/3ajtgbckh77o8). Artefatto firmato `radiotech-android-distribution` conservato 14 giorni; la distribuzione ai tester non dimostra installazione, upgrade con dati pendenti o accettazione commerciale.
- Documentazione mobile: [archivio cifrato](https://github.com/Kevinni05/gestionale_radio/blob/main/docs/encrypted-operational-storage.md), [updater verificato](https://github.com/Kevinni05/gestionale_radio/blob/main/docs/verified-android-updates.md). Migrazione lazy, originali camera/galleria e Keystore hardware non sono bonificati/certificati dai test software.
- Render: health pubblico dell’11 ottobre, 09:50:54 UTC, `UP`, `sourceRevision=a4c6fd819bb86fd1b421892e76290f93332213f6`, `revisionSource=RENDER_HOST`. Controller `/assets/control-room.js` disponibile e sintatticamente valido. Nessun ERROR restituito nel periodo 09:50–09:51:40 UTC; campione memoria massimo 312.516.600 byte su 536.870.900. Serie HTTP vuote: nessuna attestazione di carico, p95 o target letture.

### Lavoro tecnico ancora aperto

Oltre ai gate esterni, restano implementazioni: aggregazioni/proiezioni dashboard e misurazione delle letture (B06), migrazione completa storage e streaming download/cleanup (B07), migrazione di tutti gli invii immediati in outbox (B08), separazione dei controller web (W02), architettura feature/repository/stato mobile (M07), ricerca/filtri salvati e cache pack P2 (W14/M13). Non ridurre il backlog a soli test su dispositivi o pratiche commerciali.

### Gate non eseguibili con le sole prove CI

Servono staging Firebase autorizzato e verifica delle regole/indici effettivi, almeno due Android reali con dati pendenti e upgrade firmato, carico rappresentativo, modello di vendita e ownership degli account/chiavi, revisione privacy/licenze e pilota di almeno due settimane o campione equivalente concordato. La connessione Render attuale non espone la modifica di healthCheckPath o autoDeployTrigger: sono ancora osservati path vuoto e trigger commit, da allineare alla configurazione repository. Nessun gate commerciale è dichiarato superato.


## Aggiornamento del 12 ottobre 2026

Base aggiornata dai repository main; [modifiche coordinate, compatibilità e collaudo](consolidation-2026-10-12.md). B06 riduce le letture dello storico report al backlog pendente con paginazione e preserva i record legacy. Dashboard e monitor escludono i rimossi e includono APPROVAL_PENDING; restano le aggregazioni delle altre collezioni e misure rappresentative. B08/M10 aggiungono push con tenant/destinatario, deduplica persistente e deregistrazione del dispositivo al logout. M06 aggiunge resume/rete e follow-up concorrente con scope invariato. M04 corregge il messaggio di retry manuale quando restano operazioni. M07 resta parziale: il coordinatore sync è separato, la riorganizzazione completa delle feature non è conclusa.

Backup schedulato [38108262612](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38108262612) riuscito. Monitor [38179940741](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38179940741): backup OK, metriche disponibili, allarme PENDING_REPORTS su un report. Nessun allarme è stato silenziato e nessuna prova commerciale esterna viene dichiarata completata.


## Estensione implementata sui repository correnti

Consultare [consolidamento coordinato](consolidation-2026-10-12.md) e [manuali e procedure](OPERATIONS-AND-DELIVERY.md). B04 aggiunge audit delle cancellazioni asset e intenzione/recovery della cancellazione operatore. B06 aggiunge proiezioni, backfill idempotente e pagine asset; l'attivazione e il benchmark restano aperti. B07 aggiunge streaming, migrazione verificata, binding transazionale e cleanup conservativo a 30 giorni per soli upload lifecycleVersion=1; Storage effettivo e migrazione reale restano da verificare. B09 aggiunge contratto errori e timeout Auth. B10/M02 condividono checklist/versione/unità fra PDF e record, preservando gli hash legacy.

W02: dieci controller per feature e build del bundle in CI/Docker. W03: azienda/ambiente espliciti. W07: selezione mappa→riga aggiunta alla selezione lista→mappa. W14: ricerca per prefisso su indice server, autorizzazione e verifica tenant/documento corrente; cinque ricerche salvate nella sessione dell'account. Le Functions/indici/backfill della ricerca devono essere attivati prima della disponibilità reale. W09 resta da completare per la paginazione/filtri di ogni risorsa e prova di stabilità su dataset grande.

M05/M10: test SQLite/Keystore passato sul telefono fisico e whitelist ricevute corretta. M07: feature/repository e provider Home introdotti, resta una parte dell'orchestrazione nella schermata report. M08: compressione e capienza foto esplicite, memoria/prestazioni reali da misurare. M11: origine HTTPS privata configurabile anche nel downloader nativo, script/workflow manifest e avviso, versione 1.5.2+2023. M12: l'utente ha concordato un telefono più emulatore; test nativo passato sul telefono, emulatore CI predisposto. M13 non parte da zero: esistono già offline_task_pack_store e offline_packs_screen con scope account e validUntil; collaudo della checklist offline resta da registrare.

Il sito HTTPS non è ancora disponibile. Functions non sono state distribuite: il tentativo functions:list non è autorizzato/disponibile con l'accesso attuale. Nessun billing è stato modificato. Il gate commerciale resta aperto per i criteri funzionali, operativi e commerciali elencati nel piano; non confondere merge/CI/APK con completamento del pilota.

W09: paginazione per operatori, impianti e magazzino con componente condiviso, filtri inviati al server, cursori e conservazione delle pagine caricate dopo azioni/refresh; contatori del magazzino esplicitamente riferiti agli articoli caricati. Incarichi e report conservano profondità, filtri e dettagli aperti. W07: simbolo antenna SVG con nome accessibile, legenda semantica e zoom conservato durante il refresh. W14: ricerca disponibile anche senza Functions tramite fallback con letture limitate e cursore legato a tenant/query; le proiezioni accelerano la ricerca dopo attivazione. Le prove sul carico reale restano aperte.

Frontend W01–W14: vedere la [matrice di implementazione e collaudo](consolidation-2026-10-12.md#frontend-web--implementazione-e-collaudo). Sostituiti i prompt nativi con dialoghi accessibili, aggiunti errori per campo, motivazione obbligatoria per rimuovere un report, navigazione calendario da tastiera e protezione delle note non salvate. ENGINEER gestisce le operazioni ma non amministra identità/ruoli. La CI mobile [38188509492](https://github.com/Kevinni05/gestionale_radio/actions/runs/38188509492) è riuscita, incluso l'emulatore Android 34.
