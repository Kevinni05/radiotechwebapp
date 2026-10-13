# RADIO TECH — registro della candidata 1.5.5+11004

Fonte: piano Enterprise Pro 2.1 del 13 ottobre 2026 consegnato da Kevin. Le richieste operative sono quelle dell'utente; soglie, calendario e campione contenuti nel piano restano criteri di verifica, non risultati già ottenuti.

**Decisione attuale: No-Go commerciale.** Sono state corrette cause concrete di mancata registrazione/consegna push. T15/T16, collaudo sul campo e pilota richiedono ancora evidenza effettiva. Questo registro non sostituisce l'accettazione del cliente.

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
| SEC-03 | Da validare | Verifica `/auth/me` su avvio/resume/rete e ogni 5 minuti in foreground; 401/403 bloccano, fallback massimo 8 ore | Revoca/ruolo con identità reale; T10 |
| DATA-01 | Da validare | Chiavi durevoli e replay dominio; notifica dashboard atomica e ID conservato su retry/reload | T02 con timeout dopo commit in ambiente candidato |
| DATA-02 | Da validare | Transazione approvazione/stock, concorrenza e recovery verificati con Firestore emulato | T04 nel percorso web/mobile distribuito |
| DATA-03 | Da validare | Audit atomico e recovery intenti Auth; audit retry e revoca dispositivo | Retention/owner e controllo audit dell'identità attuale |
| DATA-04 | Da validare | Backup applicativo cifrato e restore isolato; nuova autorità: export/restore di 90 documenti e un segreto con chiave amministrativa separata, confronto riuscito (IDENTITY-RECOVERY-2026-10-13) | T12 completo, copia offsite amministrativa del registro, custodia separata, recupero MFA e RPO/RTO cliente |
| DATA-05 | Da validare | Snapshot/versioni/unità/checksum PDF; prove immutabilità e rinomina | Modello report e PDF accettati dal cliente |
| AUTH-01 | Da validare | Ripristino Firebase e access gate; test riavvio logico/offline/UID | Avvio e upgrade della nuova build firmata sul Samsung |
| AUTH-02 | Da validare | Rotazione token UID+tenant, cleanup seriale, scope guard; mapping FCM per dispositivo e revoca atomica | T06/T10; installazioni legacy senza mapping devono riassociare il token al login/sync |
| AUTH-03 | Da validare | Recovery distinto per rete, scadenza, sospensione e revoca; lista dispositivi e audit | Utente nuovo prova recovery e revoca dalla centrale |
| PRES-01 | Da validare | Connessioni per dispositivo; foreground/background separati, heartbeat 30 s, scadenza 90 s e orologio server; test multi-device/regole | T08: Samsung + emulatore; misurare cambio entro 10 s con rete sana |
| PRES-02 | Chiuso | La presenza contiene soltanto stato e timestamp; GPS acquisito solo nel check-in | Sorvegliare consumi nel pilota |
| NOTIF-01 | Da validare | Inbox persistente, outbox, esiti per dispositivo, errori IAM visibili e retry sullo stesso ID | Push parziale reale, token scaduto e ricevuta sul telefono |
| NOTIF-02 | Da validare | Listener anticipati, rinnovo/resume/rete, deep link generale/task, tag stabile Android e testo lock screen generico | Foreground/background/cold start; account sostituito e permesso negato |
| NOTIF-03 | In corso | Permesso FCM ripristinato; proprietario token ammesso nell'archivio Android; invio dashboard atomico | **T15/T16 bloccanti: dashboard → dati → provider → Samsung → destinazione** |
| MOB-01 | Da validare | Scheda antenna/task e azioni integrate già presenti; widget test | QR/camera e prima schermata su telefono |
| MOB-02 | Da validare | Checklist nello stesso report, versione e snapshot | Compilazione e campi obbligatori sul campo |
| MOB-03 | Da validare | Preview distanza/accuratezza, impostazioni posizione/permessi, conferma prima di EN_ROUTE | GPS reale, geofence e deroga autorizzata/audit |
| MOB-04 | Da validare | Archivio Keystore/SQLite; bozze autosalvate, back blocca errori, restore non sovrascrive dati illeggibili; test nativo Samsung riuscito | Upgrade candidata e coda/bozza reali sintetiche; T03/T06/T11 |
| MOB-05 | Da validare | Stati bozza/coda/invio/ricevuta/errore e retry idempotente | T01/T02 su rete degradata |
| MOB-06 | Da validare | Sync avvio/resume/rete, coda ispezionabile, conflitti espliciti | Recovery e conflitto tra dispositivi in ambiente candidato |
| MOB-07 | Da validare | Compressione e layout piccoli/testo grande coperti automaticamente | Camera, TalkBack e misure su Samsung |
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

- Mobile: `flutter analyze --no-pub` senza segnalazioni; **180 test** Flutter riusciti dopo correzioni di sessione, scope, bozze e GPS.
- Samsung SM-S731U, Android 16: test nativo QA dell'archivio cifrato riuscito, incluso `notification_device_owner` e cache della validazione sessione. L'app commerciale non è stata cancellata o sostituita.
- Backend: test unitari/build e suite **113 test di integrazione in 39 suite**, zero errori/fallimenti, con Firestore/Auth emulati. Includono dispatch generale idempotente e rotazione/revoca token che conserva l'altro telefono.
- Regole RTDB: tre prove di isolamento, ruoli e timestamp riuscite; resolver presenza e ID persistente notifiche: quattro test Node riusciti.
- Browser: baseline Chromium 143 test riusciti; nuovo run completo 144/146, con due casi di animazione/layout riusciti nella ripetizione mirata. Correzione aggiuntiva: Nuovo Pro disabilitato finché il catalogo autorizzato non è disponibile, e animazioni mappa sospese con reduced motion. Validazione finale collegata alla PR45 e alla CI.
- GitHub: [backup del 13 ottobre](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38380146828) riuscito; [monitor disponibilità](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38398770297) riuscito. Sono prove di backup/disponibilità, non di ricezione push.
- CI mobile 38412165580: analisi/test Flutter, compilazione e test nativi riusciti; caricamento report fallito per quota artefatti GitHub esaurita. Upload diagnostici QA resi opzionali con retention un giorno e riepilogo XML/digest sempre nei log/summary; non sostituiscono la consegna della release firmata. Attendere CI della revisione finale. La precedente ipotesi di errore percorso non era confermata dal log.
- Test nativo locale Windows: sei casi riusciti; il caso già esistente di sostituzione atomica allegati fallisce perché `File.renameTo` non sostituisce il file su Windows. Il test Android fisico dell'archivio è riuscito. Non è stata indebolita la protezione degli allegati per far passare il simulatore Windows.

Log, credenziali QA, APK/AAB/simboli e baseline configurazione sono custoditi privatamente. Non includere log Firebase completi: possono contenere variabili dell'ambiente.

## Matrice concordata e gate esterni

Kevin dispone di un solo Samsung fisico. La seconda configurazione è un emulatore Android; non viene dichiarata equivalente a un secondo telefono reale per FCM, batteria, GPS o camera. Confrontare due account/tenant con Samsung + emulatore e annotare questa variante nel verbale.

Usare account sintetici e registrare ID operazione, build, commit, revisione API, timestamp e risultato. T01–T14 e T15/T16 hanno righe separate nel [verbale di consegna](DELIVERY-ACCEPTANCE-2026-10-13.md). In particolare la prova FCM QA provider→foreground non chiude la prova dashboard→assegnazione→background/cold start.

Il pilota richiede due settimane di uso reale o un campione equivalente **concordato**: non si riduce unilateralmente. Restano da nominare partecipanti, supporto e responsabili; nessun contatto o consenso è inventato. Restano da misurare p95, crash rate, costi/RPO/RTO e da approvare modello report/retention/limiti commerciali.

## Rollback della configurazione del 13 ottobre

Baseline privata: `.radiotech/cloud/notification-config-20261013.json`. Se il problema è relativo solo alla nuova URL, rimuovere `FIREBASE_DATABASE_URL` con `gcloud run services update radiotech-web --region=europe-west1 --project=gestionale-radio --remove-env-vars=FIREBASE_DATABASE_URL`; registrare la nuova revisione pronta. Rimuovere il ruolo FCM solo se il file prova che era assente prima e se si vuole sospendere anche gli invii già autorizzati in coda. Non revocare gli altri ruoli del runtime. Il rollback codice deve mantenere compatibilità con mobile 11002; un rollback mobile richiede una build correttiva con codice superiore a 11004, stessa firma e nessuna disinstallazione con dati pendenti.

PR di revisione: [web/API PR45](https://github.com/Kevinni05/radiotechwebapp/pull/45), [mobile PR30](https://github.com/Kevinni05/gestionale_radio/pull/30). Entrambe draft; nessun Go/No-Go commerciale implicito. La prova FCM fisica non è partita perché il Samsung era scollegato; fixture e credenziali QA sono state rimosse. Lo script può preparare una nuova prova dopo il collegamento.

## Chiusura della preparazione tecnica

Mobile commit `c12b50cff1d24a9772c867dc922195ab9c63b114`: [CI finale](https://github.com/Kevinni05/gestionale_radio/actions/runs/38415090536) verde, incluse compilazione, prove native e recovery/emulatore. APK/AAB **1.5.5+11004** firmati, identità/certificato originali verificati con apksigner/aapt e jarsigner/keytool. Artefatti e simboli privati; [manifest con digest](RELEASE-CANDIDATE-11004.json). Non pubblicati né installati sopra l’app commerciale.

Backend: 191 test unitari eseguiti senza fallimenti, 114 emulator case saltati in quel run; separatamente 113 test Firestore/Auth riusciti. Il caso dispositivi ora passa dalla route HTTP effettiva `/api/v1/operator/me/fcm-token`, oltre a provare transazioni/revoca, mantenendo compatibilità dei client legacy. Autorità: 28 test riusciti e restore reale→emulatore di 90 documenti/un segreto con confronto completo.

Le sei prove browser mirate finali (notifiche, Pro in caricamento, animazioni/layout) sono riuscite. Rimangono da collegare gli esiti della CI web alla revisione finale dopo il consolidamento del percorso HTTP e del recupero del registro. Manuale di recupero amministrativo: [IDENTITY-RECOVERY](IDENTITY-RECOVERY-2026-10-13.md).
