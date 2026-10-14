# RADIO TECH — registro della candidata 1.5.5+11005

Aggiornamento 14/10, collaudo integrato: [verbale completo](COLLAUDO-COMPLETO-2026-10-14.md) e [manifest corrente 11005](RELEASE-CANDIDATE-11005.json). Il flusso operativo QA sul campo e l'upgrade commerciale firmato senza disinstallazione sono riusciti. L'autorità 00006 è stata attivata sul servizio pubblico su autorizzazione esplicita per correggere il token con audience errata; accesso normale riuscito, confermato da Kevin. Backend e regole candidati restano distinti dal servizio pubblico. I riferimenti 11004 successivi descrivono prove e pacchetti storici, conservati. I gate pilota/consegna restano aperti.

Fonte: piano Enterprise Pro 2.1 del 13 ottobre 2026 consegnato da Kevin. Le richieste operative sono quelle dell'utente; soglie, calendario e campione contenuti nel piano restano criteri di verifica, non risultati già ottenuti.

**Decisione attuale: No-Go commerciale.** Il 14 ottobre sono riuscite sei prove dashboard→Samsung nei tre stati dell'app, invio multiplo a due account sequenziali con retry senza duplicati, recupero della foto cifrata e revoca/nuovo accesso. Restano verifica dopo distribuzione, ricezione simultanea concordata, upgrade firmato, flusso completo sul campo, custodia/assistenza e pilota. Questo registro non sostituisce l'accettazione del cliente. [Evidenza aggiornata Samsung](SAMSUNG-QA-2026-10-14.md).

## Baseline e configurazione verificata

- Web/API: base Git `c591ba1a61356244b1807551fb547bce613d3f58`; ramo `improve/enterprise-plan-20261013`.
- Mobile: base Git `93e89d70e48d54c6b56d55fe7a51663c007f6056`, pubspec **1.5.4+11003**; ramo `fix/mobile-notification-recovery-20261013`, [PR30](https://github.com/Kevinni05/gestionale_radio/pull/30). La precedente nota che indicava 1.5.3 come versione del sorgente era errata; 1.5.3+11002 era l'app installata sul Samsung.
- Candidata mobile: **1.5.5+11004**, stessa identità Android e firma esistente; build QA con applicationId separato. La candidata non viene dichiarata pubblicata dal solo incremento di versione.
- Backend pubblico: Render inoltra a Cloud Run `radiotech-web`, regione `europe-west1`; runtime `radiotech-runtime@gestionale-radio.iam.gserviceaccount.com`.
- Prima della riparazione: revisione `radiotech-web-00003-4f9`, URL RTDB assente, runtime senza `cloudmessaging.messages.create`. Campione outbox: assegnazione del 13 ottobre con zero dispositivi accettati e sette errori UNKNOWN; arrivata a DEAD_LETTER dopo otto tentativi. Non è stata forzata una nuova comunicazione agli operatori: usare il retry esplicito dello stesso ID dalla candidata autorizzata. La mancanza del permesso è confermata; il solo codice UNKNOWN non identificava la causa.
- Riparazione mirata applicata: ruolo `roles/firebasecloudmessaging.admin` e URL dell'istanza RTDB già esistente; revisione pronta `radiotech-web-00004-5sv`. `/api/v1/auth/public-config` espone l'URL europeo corretto. Nessun nuovo progetto, database, abbonamento o privilegio Auth amministrativo. Regole RTDB pubblicate dopo 15 test regole riusciti, con baseline privata salvata (SHA256 7b07d7b7e228587adc134facae07df813f973a2197a171cbd6b014daee25cb14). Il codice candidato web/backend richiede ancora distribuzione distinta.
- Script ripetibile: `python scripts/repair-notification-config.py` legge soltanto; `--apply --evidence PATH_PRIVATO` salva baseline e modifica solo i due elementi sopra. Il runtime può riprendere gli invii già in coda. La prova QA invia solo a un ricevitore sintetico del proprietario.

## Requisiti e prove residue

“Da validare” significa che esistono codice/prove automatiche ma manca almeno una parte del criterio integrato o dell'accettazione. Le evidenze storiche del 12 ottobre non vengono attribuite automaticamente alla nuova autorità di identità del 13 ottobre.

| ID | Stato | Implementazione / evidenza | Criterio residuo |
|---|---|---|---|
| SEC-01 | Da validare | Matrice ruoli/tenant, ENGINEER; suite API, emulatori e regole | Matrice cliente sul backend distribuito con MFA indipendente |
| SEC-02 | Da validare | Tenant su API/regole; bozze/coda/allegati e ricevute separati; chiusura schermate al cambio tenant/ruolo | T07 su candidata distribuita e due aziende QA |
| SEC-03 | Da validare | Verifica `/auth/me` su avvio/resume/rete e ogni 5 minuti; fallback massimo 8 ore; revoca dispositivo fisica e guardie Firestore/Storage candidate verificate | Revoca account/ruolo e regole nell'ambiente distribuito; T10 |
| DATA-01 | Da validare | Chiavi durevoli e replay dominio; notifica dashboard atomica e ID conservato su retry/reload | T02 con timeout dopo commit in ambiente candidato |
| DATA-02 | Da validare | Transazione approvazione/stock, concorrenza e recovery verificati con Firestore emulato | T04 nel percorso web/mobile distribuito |
| DATA-03 | Da validare | Audit atomico e recovery intenti Auth; audit retry e revoca dispositivo | Retention/owner e controllo audit dell'identità attuale |
| DATA-04 | Da validare | Backup applicativo cifrato e restore isolato; nuova autorità: export/restore di 90 documenti e un segreto con chiave amministrativa separata, confronto riuscito (IDENTITY-RECOVERY-2026-10-13) | T12 completo, copia offsite amministrativa del registro, custodia separata, recupero MFA e RPO/RTO cliente |
| DATA-05 | Da validare | Snapshot/versioni/unità/checksum PDF; prove immutabilità e rinomina | Modello report e PDF accettati dal cliente |
| AUTH-01 | Da validare | Ripristino Firebase e access gate; test riavvio logico/offline/UID | Avvio e upgrade della nuova build firmata sul Samsung |
| AUTH-02 | Da validare | Rotazione UID+tenant, cleanup seriale, scope guard; logout A/login B Samsung con inbox/ricevute separate; revoca del solo dispositivo A e altro device conservato | T06 completo bozza/coda e T10 account/ruolo; legacy devono riassociare il token al login/sync |
| AUTH-03 | Da validare | Recovery distinto per rete/scadenza/sospensione/revoca; 401 revoca corretto; nuovo accesso reale Samsung riattiva lo stesso ID | Utente nuovo prova recovery; verifica distribuita |
| PRES-01 | Da validare | Connessioni per dispositivo; foreground/background separati, heartbeat 30 s, scadenza 90 s e orologio server; test multi-device/regole | T08: Samsung + emulatore; misurare cambio entro 10 s con rete sana |
| PRES-02 | Chiuso | La presenza contiene soltanto stato e timestamp; GPS acquisito solo nel check-in | Sorvegliare consumi nel pilota |
| NOTIF-01 | Da validare | Inbox/outbox, esiti per dispositivo; PARTIAL con B NO_TOKENS → registra B → retry SENT dello stesso ID, ricevuta/apertura fisiche | Token scaduto/permesso negato e percorso distribuito |
| NOTIF-02 | Da validare | Listener anticipati, rinnovo/resume/rete, deep link generale/task, tag stabile Android e testo lock screen generico | Foreground/background/cold start; account sostituito e permesso negato |
| NOTIF-03 | Da validare | Sei prove nei tre stati; multipli A/B sequenziali sul Samsung e retry senza duplicazione riusciti | **T15 simultaneo e T15/T16 dopo distribuzione governata** |
| MOB-01 | Da validare | Scheda antenna/task e azioni integrate già presenti; widget test | QR/camera e prima schermata su telefono |
| MOB-02 | Da validare | Checklist nello stesso report, versione e snapshot | Compilazione e campi obbligatori sul campo |
| MOB-03 | Da validare | Preview distanza/accuratezza e permesso preciso provati sul Samsung; prima di confermare resta ACCEPTED senza check-in | Check-in reale nel geofence e deroga autorizzata/audit |
| MOB-04 | Da validare | Archivio Keystore/SQLite; recovery seed/restart/upgrade QA riuscito; foto bozza recuperata dopo aggiornamento e restaging cifrato verificato | Upgrade commerciale firmato e T06 completo; T11 |
| MOB-05 | Da validare | Stati bozza/coda/invio/ricevuta/errore e retry idempotente | T01/T02 su rete degradata |
| MOB-06 | Da validare | Sync avvio/resume/rete, coda ispezionabile, conflitti espliciti | Recovery e conflitto tra dispositivi in ambiente candidato |
| MOB-07 | Da validare | Camera/anteprima foto recuperata/PDF verificati Samsung; semantica foto e rimozione coperta automaticamente | TalkBack fisico declinato da Kevin; misure e restante T14 |
| WEB-01 | Da validare | Componenti condivisi, suite browser e prove visuali esistenti | Revisione cliente della candidata |
| WEB-02 | Da validare | Topbar/azienda/navigazione responsive e tastiera | T14 e verifica ambiente distribuito |
| WEB-03 | Da validare | Dashboard urgenze/KPI, meteo non bloccante e fallback | Freschezza KPI e tempo utilizzo misurati |
| WEB-04 | Da validare | Schede Operatori e presenza distinta da disponibilità | Presenza reale e interazione con filtri/azioni |
| WEB-05 | Da validare | Centro report, approva/rimuovi autorizzati e audit | Responsabile prova dettaglio e storico |
| WEB-06 | Da validare | Simbolo verde e stato anche in testo/forma; lista/mappa coerenti | Revisione visuale cliente |
| WEB-07 | Da validare | Calendario/magazzino con versioni, conflitti e motivazioni | Flusso utente e stock del collaudo integrato |
| WEB-08 | Da validare | Vuoto/retry/errore/permessi, focus/tastiera/zoom e dialog testati | Accettazione accessibilità T14 |
| PERF-01 | Da validare | Paginazione e aggregazioni; fixture 10.010 task trasferisce 10 attivi | Dataset identico, p95 caldo e letture fatturate prima/dopo |
| PERF-02 | Da validare | Refresh coordinato e listener solo viste visibili; presence timer interrotto fuori vista | Misura carico idle e tab nascosta |
| PERF-03 | Da validare | Streaming con digest/staging, limiti, migrazione/checkpoint e rollback | Memoria/byte sotto carico e retention cliente |
| OPS-01 | Da validare | Monitor disponibilità e backup GitHub riusciti; push status/errori/azioni visibili | Owner, soglie, budget e prova escalation |
| OPS-02 | Da validare | Branch/candidata versionati, package QA separato e manifest privato | Staging cliente e riconciliazione digest/revisioni finali |
| MAINT-01 | Chiuso | Moduli feature web, dispatch notifiche e resolver presenza estratti; servizi mobili con dipendenze controllabili | Manutenzione ordinaria |
| REL-01 | Da validare | Canale scelto: APK firmato su area HTTPS autenticata esistente | Consegna candidata governata e destinatari definiti |
| REL-02 | Da validare | Compatibilità client precedente e procedura correttiva con versionCode crescente | T11 e rollback backend effettivo della candidata |
| REL-03 | Da validare | Identità keyless, niente Auth admin al runtime; regole, indici e firma verificabili | Checklist ambiente candidato; nessun seed/QA di test nel rilascio |
| REL-04 | Da validare | Manuali tecnico/responsabile/amministratore in OPERATIONS-AND-DELIVERY | Utente nuovo completa T01 senza sviluppatore |
| REL-05 | Da validare | Provisioning, export/restore e procedure di uscita disponibili | Secondo ambiente cliente configurato e export verificato |
| REL-06 | Da validare | Pacchetto consegna con campi compilabili autorizzato da Kevin | Contatto assistenza, owner hosting/backup/firma e condizioni concordate |

## Evidenze di questa iterazione

Audit della Panoramica: in una risposta parziale i conteggi mancanti diventavano zero, producendo anche “100%” insieme a “0 / 1 attive”. La candidata distingue valori sconosciuti (`—`/badge neutro), percentuali non valide e zero esplicito, senza ricostruire conteggi da liste parziali. Nuove prove coprono questi tre stati. Il confronto visuale conserva dimensioni/stili identici; un artefatto CI mostrava 14 pixel variati su 2.448.320, uno solo di 2/255 su un bordo. Il controllo conserva 1/255 generale e permette 2/255 solo a un pixel per milione. Il confronto mirato è passato due volte per ciascuno di Chromium, Firefox e WebKit.

Verifica finale delle pagine modificate: **36 casi** Panoramica, KPI e workspace responsive riusciti su Chromium/Firefox/WebKit. I conteggi assenti di antenne, operatori, interventi, stock e stati task sono mostrati come non disponibili; zeri espliciti restano zeri. Nessuna lista parziale viene usata per ricostruire le metriche globali.

Aggiornamento del retry dopo rinnovo token: gli esiti sono calcolati sulle registrazioni selezionate per il tentativo corrente. I token precedenti restano nelle ricevute backend e nei contatori `previousTokenAccepted`/`previousTokenFailures`, senza mantenere un falso DEAD_LETTER dopo la consegna al nuovo token. Provate le transizioni token scaduto → nessun token → token rinnovato, con stessa notifica e conservazione delle ricevute: sette test integrati di consegna/storico/dispatch riusciti. Sono prove emulatore/provider simulato, non ricezione fisica FCM.

- Mobile: `flutter analyze --no-pub` senza segnalazioni; **180 test** Flutter riusciti dopo correzioni di sessione, scope, bozze e GPS.
- Samsung SM-S731U, Android 16: test nativo QA dell'archivio cifrato riuscito, incluso `notification_device_owner` e cache della validazione sessione. L'app commerciale non è stata cancellata o sostituita.
- Backend: test unitari/build e suite **113 test di integrazione in 39 suite**, zero errori/fallimenti, con Firestore/Auth emulati. Includono dispatch generale idempotente e rotazione/revoca token che conserva l'altro telefono.
- Regole RTDB: tre prove di isolamento, ruoli e timestamp riuscite; resolver presenza e ID persistente notifiche: quattro test Node riusciti.
- Browser: baseline Chromium 143 test riusciti; nuovo run completo 144/146, con due casi di animazione/layout riusciti nella ripetizione mirata. Correzione aggiuntiva: Nuovo Pro disabilitato finché il catalogo autorizzato non è disponibile, e animazioni mappa sospese con reduced motion. Validazione finale collegata alla PR45 e alla CI.
- GitHub: [backup del 13 ottobre](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38380146828) riuscito; [monitor disponibilità](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38398770297) riuscito. Sono prove di backup/disponibilità, non di ricezione push.
- CI mobile 38412165580: analisi/test Flutter, compilazione e test nativi riusciti; caricamento report fallito per quota artefatti GitHub esaurita. Upload diagnostici QA resi opzionali con retention un giorno e riepilogo XML/digest sempre nei log/summary; non sostituiscono la consegna della release firmata. Attendere CI della revisione finale. La precedente ipotesi di errore percorso non era confermata dal log.
- Test nativo locale Windows: sei casi riusciti; il caso già esistente di sostituzione atomica allegati fallisce perché `File.renameTo` non sostituisce il file su Windows. Il test Android fisico dell'archivio è riuscito. Non è stata indebolita la protezione degli allegati per far passare il simulatore Windows.

Log, credenziali QA, APK/AAB/simboli, JAR backend e baseline configurazione sono custoditi privatamente. Il JAR della revisione `15ad8e8` include la correzione finale dei KPI ed è incluso nel pacchetto 11004 e nel manifest con SHA256/byte; il commit della configurazione distribuita rimane distinto dalla candidata. Non includere log Firebase completi: possono contenere variabili dell'ambiente.

## Matrice concordata e gate esterni

Kevin dispone di un solo Samsung fisico. La seconda configurazione è un emulatore Android; non viene dichiarata equivalente a un secondo telefono reale per FCM, batteria, GPS o camera. Confrontare due account/tenant con Samsung + emulatore e annotare questa variante nel verbale.

Usare account sintetici e registrare ID operazione, build, commit, revisione API, timestamp e risultato. T01–T14 e T15/T16 hanno righe separate nel [verbale di consegna](DELIVERY-ACCEPTANCE-2026-10-13.md). In particolare la prova FCM QA provider→foreground non chiude la prova dashboard→assegnazione→background/cold start.

Il pilota richiede due settimane di uso reale o un campione equivalente **concordato**: non si riduce unilateralmente. Restano da nominare partecipanti, supporto e responsabili; nessun contatto o consenso è inventato. Restano da misurare p95, crash rate, costi/RPO/RTO e da approvare modello report/retention/limiti commerciali.

## Rollback della configurazione del 13 ottobre

Baseline privata: `.radiotech/cloud/notification-config-20261013.json`. Se il problema è relativo solo alla nuova URL, rimuovere `FIREBASE_DATABASE_URL` con `gcloud run services update radiotech-web --region=europe-west1 --project=gestionale-radio --remove-env-vars=FIREBASE_DATABASE_URL`; registrare la nuova revisione pronta. Rimuovere il ruolo FCM solo se il file prova che era assente prima e se si vuole sospendere anche gli invii già autorizzati in coda. Non revocare gli altri ruoli del runtime. Il rollback codice deve mantenere compatibilità con mobile 11002; un rollback mobile richiede una build correttiva con codice superiore a 11004, stessa firma e nessuna disinstallazione con dati pendenti.

PR di revisione: [web/API PR45](https://github.com/Kevinni05/radiotechwebapp/pull/45), [mobile PR30](https://github.com/Kevinni05/gestionale_radio/pull/30). Entrambe draft. Il Samsung scollegato aveva impedito le prime prove il 13 ottobre; gli esiti fisici del 14 ottobre sono nel registro aggiornato.

## Evidenze storiche del 13 ottobre

I commit e digest sotto appartengono alla candidata precedente conservata nel pacchetto privato del 13 ottobre. Per gli artefatti attuali usare esclusivamente il manifest aggiornato e l'appendice del 14 ottobre.

Ultima revisione mobile `47c7103a05dada60e4038ec4f343da4ea329dfe7`: le cache della schermata principale vengono eliminate al cambio UID/tenant/ruolo. La nuova prova verifica la scomparsa dei dati di A dal root dopo il passaggio a B con lo stesso UID. Analisi pulita e **181 test** riusciti; APK/AAB ricompilati con identità/certificato originali, digest definitivi nel [manifest 11004](RELEASE-CANDIDATE-11004.json). [CI ultima revisione](https://github.com/Kevinni05/gestionale_radio/actions/runs/38423238699). La CI verde del commit precedente rimane un'evidenza distinta.

La [CI web/API del commit 84af23a](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38421571123) include la route mobile effettiva e il backup del registro; test locali e restore sono riusciti. Il successivo aggiornamento riguarda solo documenti e digest della candidata.

Mobile commit `c12b50cff1d24a9772c867dc922195ab9c63b114`: [CI finale](https://github.com/Kevinni05/gestionale_radio/actions/runs/38415090536) verde, incluse compilazione, prove native e recovery/emulatore. APK/AAB **1.5.5+11004** firmati, identità/certificato originali verificati con apksigner/aapt e jarsigner/keytool. Artefatti e simboli privati; [manifest con digest](RELEASE-CANDIDATE-11004.json). Non pubblicati né installati sopra l’app commerciale.

Backend: 191 test unitari eseguiti senza fallimenti, 114 emulator case saltati in quel run; separatamente 113 test Firestore/Auth riusciti. Il caso dispositivi ora passa dalla route HTTP effettiva `/api/v1/operator/me/fcm-token`, oltre a provare transazioni/revoca, mantenendo compatibilità dei client legacy. Autorità: 28 test riusciti e restore reale→emulatore di 90 documenti/un segreto con confronto completo.

Le sei prove browser mirate finali (notifiche, Pro in caricamento, animazioni/layout) sono riuscite. Rimangono da collegare gli esiti della CI web alla revisione finale dopo il consolidamento del percorso HTTP e del recupero del registro. Manuale di recupero amministrativo: [IDENTITY-RECOVERY](IDENTITY-RECOVERY-2026-10-13.md).

## Consolidamento del 14 ottobre

Sorgente commerciale mobile **f304070**, APK/AAB **1.5.5+11004** ricompilati dopo la correzione foto e 401 revoca: **184 test Flutter**, analisi pulita, **8 test nativi Linux** e recovery Android emulato riusciti. [CI mobile](https://github.com/Kevinni05/gestionale_radio/actions/runs/38489631207). La sonda QA successiva isola anche Firestore SDK nell'emulatore; la prima configurazione aveva effettuato soltanto letture reali delle antenne del tenant, mentre tutte le scritture operative API erano emulatore.

Web candidato **34dc074**: [CI interamente verde](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38495119175). Autorità 35 prove, backend 193 test unitari eseguiti e 114 emulator case separati dal run unitario; regole 17 prove, integrazioni Firestore/Auth, tre browser, container e build riproducibile. Il nuovo endpoint dell'autorità verifica una prova recente reale prima di firmare la sessione di dispositivo; firmatario isolato del progetto Auth senza ruoli applicativi, runtime web privo di nuovi privilegi Auth/IAM.

Prove Samsung: tre generali e tre incarichi nei tre stati; multipli A/B sequenziali, PARTIAL→SENT mediante retry sul medesimo ID; separazione inbox/ricevute al cambio account. Revoca A dalla dashboard e nuova autenticazione riuscite. B, con record proprio revocato soltanto nell'emulatore: API 401 e SDK Firestore permission-denied dopo precedente lettura consentita. GPS preciso/preview annullata, foto recuperata da bozza, PDF/restaging cifrato riusciti. TalkBack fisico non eseguito per preferenza esplicita di Kevin.

Pacchetto corrente privato `1.5.5-11004-rc-20261014-f304070`: APK, AAB, simboli e JAR backend dal sorgente **9afdb5c**, più documenti ed evidenze selezionate. Manifest [RELEASE-CANDIDATE-11004](RELEASE-CANDIDATE-11004.json) verificato rispetto ai byte. APK con identità/firma originali; tutti i 484 payload AAB verificati con il medesimo certificato. La build contiene il normale entrypoint, non le credenziali delle sonde QA. Il pacchetto storico precedente è conservato separatamente.

Autorità candidata `radiotech-identity-00006-vuc` a zero traffico ordinario; `00004-nbf` resta al 100%. Backend pubblico ancora `radiotech-web-00004-5sv`; regole Firestore/Storage nuove non pubblicate. Prima della distribuzione delle regole Storage verificare il service agent per le letture Firestore, come descritto nel registro Samsung. La pubblicazione/accettazione resta subordinata ai gate espliciti del verbale.

Collaudo chiuso: tag temporaneo rimosso, account sintetici e presenze propri eliminati, server/browser e reverse USB QA arrestati. Il PDF è conservato nel pacchetto privato ed eliminato dal telefono; bozza sintetica cifrata conservata. Il backend pubblico e il traffico ordinario dell'autorità non sono stati promossi alla candidata.
