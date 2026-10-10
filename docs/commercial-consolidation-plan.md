# RADIO TECH — Piano di consolidamento e preparazione alla vendita
Versione 1.0 · 10 ottobre 2026 · Audit dei sorgenti correnti e delle evidenze CI

## 1. Valutazione e perimetro

RadioTech possiede già una base funzionale significativa. La prossima release deve consolidare affidabilità operativa, coerenza dei flussi e qualità dell'esperienza prima di ampliare il catalogo delle funzioni. La CI verde dimostra che i controlli implementati passano; non certifica da sola l'idoneità commerciale.

Baseline:
- Web/backend: Kevinni05/radiotechwebapp, main, 477bebcd6af759ed7f94a2665cbd6068efa39a84.
- Mobile: Kevinni05/gestionale_radio, main, 361efcf99f3df7f2177ba10ead3c7927577a3c00; pubspec 1.5.1+2022.
- CI web 38073647535: cinque job riusciti, inclusi emulatori, browser, container e riproducibilità JAR.
- CI mobile 38071759045: analisi, test, compilazione e invio effettivo a Firebase App Distribution riusciti. Questo aggiorna la precedente informazione sul job saltato: lo step di distribuzione della versione corrente è stato eseguito.
- Monitor 38087075824 e 38083198926: health riuscito; operations fallito per backup=STALE. Log rispettivamente del 10 ottobre, 21:18 e 20:18 UTC.
- Il commit effettivamente in esecuzione su Render resta da confrontare con la baseline mediante metadati della release.

Metodo: inventario ricorsivo di entrambi i repository, revisione mirata di circa 60 file tra servizi, sicurezza, interfacce, configurazioni, test e workflow, lettura dei risultati CI e dei log operativi. È un audit statico approfondito delle aree critiche, non una revisione riga per riga di ogni file. Non sono stati eseguiti nuovi test locali né una sessione autenticata completa sul prodotto distribuito. Non sono state apportate modifiche, merge o deploy.

Le raccomandazioni visive sono specifiche di prodotto ma richiedono validazione delle schermate reali. Non attribuiamo punteggi estetici senza osservazione diretta. Non è stata verificata la configurazione reale di IAM, billing, App Check enforcement, segreti, protezione branch o proprietà degli account del cliente.

Assunzione commerciale: primo cliente B2B, uso italiano, Android per gli operatori e control room web per i responsabili. Preferire inizialmente un ambiente dedicato per cliente, mantenendo il modello tenant esistente. Un SaaS condiviso richiede ulteriori prove di isolamento, quote, amministrazione e supporto.

“Definitivo” significa perimetro di release congelato e criteri di uscita verificabili. Sicurezza, dipendenze e compatibilità continueranno a richiedere manutenzione.

## 2. Elementi già presenti da valorizzare

| Area | Evidenza corrente | Implicazione |
|---|---|---|
| Identità | Firebase token verificato con controllo revoca; claims tenant; ruoli server | Consolidare la matrice delle capacità |
| Isolamento | TenantAccessPolicy, filtri tenant, regole client restrittive | Estendere prove negative per ogni risorsa |
| Operazioni | Stati task, check-in, report, approvazione e magazzino | Verificare recupero dai fallimenti intermedi |
| Integrità report | Idempotenza, riferimenti opachi, digest allegati | Conservare compatibilità durante migrazioni |
| Rimozione report | Soft delete e audit nella stessa transazione | Non sostituire con cancellazione fisica indiscriminata |
| Offline mobile | Retry, backoff, chiavi persistenti, doppia copia con revisione, scope utente/tenant | Rafforzare persistenza e lifecycle |
| Backend production | Validazioni di avvio, niente emulatori/seeding, limiti richieste, logging strutturato | Provare configurazione realmente distribuita |
| Distribuzione | APK firmato e Firebase Distribution corrente riuscita; workflow APK/AAB di produzione | Separare collaudo e canale commerciale |
| Recovery | Workflow cifrato e strumenti di backup/drill già presenti | Risolvere freschezza e provare un ripristino reale |
| UI | Tema scuro, design system, responsive, riduzione animazioni, test browser | Consolidare componenti e ridurre sovrapposizioni |

## 3. Registro delle evidenze e dei rischi

Priorità: P0 blocca il via libera commerciale; P1 va chiuso nella release di consolidamento; P2 è evoluzione o requisito condizionale. “Verificato” descrive il codice o i log osservati, non una vulnerabilità sfruttata.

| ID | Priorità | Evidenza | Rischio / lavoro richiesto |
|---|---|---|---|
| E01 | P0 | Due monitor operations segnalano backup STALE; health passa | Verificare ultimo archivio valido e ultimo restore; ripristinare la protezione operativa |
| E02 | P1 | DashboardService legge intere collezioni tenant per statistiche, incluse maintenanceReports | Letture e latenza crescono con dati e utenti; aggregazioni e proiezioni |
| E03 | P1 | control-room aggiorna otto aree ogni 60 secondi tramite refreshAll | Dati riletti anche fuori dalla vista corrente; deduplicazione, invalidazioni e sospensione tab nascosta |
| E04 | P1 | Allegati production default FIRESTORE, blocchi 256 KiB, risposta base64; save synchronized | Costi, memoria e serializzazione upload; usare adapter object storage già esistente con migrazione verificata |
| E05 | P0/P1 | AuditService invia eventi asincroni e registra solo warning al fallimento | Per azioni critiche, possibile operazione riuscita senza audit durevole; transazione o outbox |
| E06 | P1 | deliverRecorded marca SENT con token presenti anche con successCount inferiore al totale | Stato aggregato può nascondere fallimenti; distinguere esito parziale e retry per destinatario |
| E07 | P1 | removeInvalidTokens rileva e logga; non rimuove effettivamente i token | Token inutili e consegne ripetutamente fallite; cleanup della relazione token/dispositivo |
| E08 | P0/P1 | Report closure invia segnale urgente prima del report; sendCentralUrgentSignal genera operationId nuovo a ogni chiamata | Retry dopo fallimento report può creare segnali distinti; chiave persistente e recupero fase per fase |
| E09 | P1 | Coda e bozze in SharedPreferences; foto in file locali | Non equivalgono a archivio cifrato transazionale; migrazione senza perdita e protezione dati locali |
| E10 | P1 | Offline timer periodico; meccanismo mostrato dipende dal processo app | Non promettere sincronizzazione garantita ad app terminata; definire lifecycle e comportamento background |
| E11 | P0/P1 | ENGINEER ha privilegi in TenantAccessPolicy ma non nelle liste route web/operator né nella lista mobile/regole | Matrici discordanti; definire ruolo e allineare tutti gli strati con test |
| E12 | P1 | Template web circa 3.096 righe/204 KiB; home mobile 2.063 righe; report closure 1.699; OperatorService 2.165 | Modifiche fragili; estrazione incrementale per feature |
| E13 | P1 | Web conserva ID e refresh token in localStorage o sessionStorage | Impatto maggiore in caso di XSS; progettare sessione web e rafforzare DOM/CSP |
| E14 | P1 | RateLimitFilter tiene stato per istanza e chiave IP+URI | Coerenza con repliche e URI variabili; normalizzare route e validare proxy fidati |
| E15 | P1 | Playwright usa Chromium, Firebase disabilitato e fixture che intercettano API; mobile usa mock/widget test | Mancanza di prova integrata mobile→API→Firestore→web nelle evidenze esaminate |
| E16 | P1 | Workflow release parametrizza applicationId/nome; updater li fissa a com.example.gestionale_radio e RadioTech | Personalizzazione può interrompere aggiornamenti; contratto release unico |
| E17 | P1 | Updater legge digest manifest ma apre download esterno | Digest dichiarato non è verifica dei byte scaricati nell'app; verificare canale scelto e firma installazione |
| E18 | P1 | Approva report: claim, consume, task update, finalize sono fasi separate con idempotenza/lease | Non è una singola transazione globale; prova crash dopo ogni fase e riconciliazione |
| E19 | P2/P1 | Esistono numerosi CSS/JS di rifinitura e componenti Flutter con stili inline | Coerenza visiva da verificare e consolidare; non basta aggiungere un altro override |
| E20 | P0 | Nessuna verifica diretta di device, configurazione Render, IAM e ripristino reale in questo audit | Chiudere gap di evidenza prima di dichiarare prodotto pronto |

Il monitor successivo riuscito non prova da solo che il backup sia stato rinnovato: occorre confrontare timestamp, archivio e risultato restore. Anche l'assenza di un run backup sullo SHA corrente non prova che i backup non esistano: possono essere stati prodotti su commit precedenti.

Per E08 il backend usa operationId per deduplicare: il problema proposto riguarda la stabilità della chiave tra tentativi client, non l'assenza di idempotenza server. Per E18 esistono già meccanismi di recupero: prima ampliarli, verificare ciò che coprono.

## 4. Architettura obiettivo

Conservare Java 21/Spring Boot, Firebase e Flutter. Per il web mantenere l'implementazione corrente durante il consolidamento: non è necessario migrare subito a React/Next.js. Separare template, controller UI, servizi API e componenti per feature. Una migrazione di framework avrebbe costi e rischio senza risolvere automaticamente i problemi osservati.

Backend come monolite modulare: identity/tenant, asset, task, report, inventory, notification, workforce e operations. Gli endpoint delegano; i servizi applicativi orchestrano; policy di dominio esprimono stati e permessi; repository/adapters incapsulano Firestore, FCM e file. Evitare microservizi prima di una necessità misurata.

Firestore conserva dati strutturati, riferimenti, audit e stato delle operazioni. Object storage conserva PDF, foto e firme. Le notifiche e l'audit critico usano eventi durevoli con stato, tentativi e riconciliazione. I processi asincroni portano tenant e attore esplicitamente; non dipendono dal SecurityContext della richiesta originaria.

Mobile: organizzare per feature, DTO tipizzati e repository; Riverpod già presente per stato asincrono. Persistenza locale transazionale, protetta e con schema migrabile; coda per utente/tenant; sincronizzazione osservabile. Non trasferire le policy autoritative dal server al client.

Contratto condiviso: OpenAPI mantenuto, codici errore stabili, stati canonici, versione delle checklist, idempotencyKey, version/ETag per conflitti, timestamps UTC e unità tecniche. Compatibilità documentata almeno con la versione mobile precedente durante gli aggiornamenti.

## 5. Piano backend e dati

### B01 — Recovery e backup (P0, responsabile platform)
Verificare configurazione del workflow esistente, segreti e ultimo archivio; controllare se il record systemOperations/backup è coerente. Eseguire export e ripristino in ambiente isolato, confrontando documenti, identità/claims e byte degli allegati. Includere regole, indici e artefatti.
Accettazione: archivio recente verificato, restore completo dimostrato, chiave recuperabile separatamente, allarme su mancato backup e owner definito. Target iniziale proposto RPO ≤24h/RTO ≤4h, da confermare con cliente e misurare; non è uno SLA già disponibile.

### B02 — Permessi e isolamento (P0, backend)
Definire capacità per ruolo, incluse ENGINEER, VIEWER, CUSTOMER e amministrazione account. Allineare route, method checks, regole Firebase e UI. Verificare anche riferimenti indiretti: task→antenna, report→task, allegato→report, destinatario→azienda. Gli SDK server bypassano le regole client: ogni operazione server deve applicare la policy tenant.
Accettazione: matrice di test ruolo×endpoint×tenant; 403/404 coerenti; nessuna lettura o modifica tra aziende; revoca ed account sospeso testati.

### B03 — Integrità workflow (P0, backend/mobile)
Formalizzare gli stati esistenti in una matrice delle transizioni, incluse eccezioni: check-in già fatto, GPS negato, report rifiutato, doppio invio, doppia approvazione, rimozione durante approvazione. Conservare le operazioni idempotenti attuali; aggiungere riconciliazione degli stati intermedi dove necessaria.
Accettazione: interrompere processo dopo upload, creazione report, consumo materiali e aggiornamento task; riavviare e completare senza duplicare consumi né lasciare stati irrecuperabili.

### B04 — Audit critico (P0, backend)
Per modifiche ruoli, approvazioni, magazzino e azioni distruttive, registrare evento o outbox nello stesso commit della mutazione quando possibile. Prevedere retry e allarme per eventi pendenti. Definire quali dati prima/dopo conservare evitando credenziali e contenuti sensibili superflui.
Accettazione: ogni azione critica ha un evento durevole; errore audit non viene nascosto; consultazione tenant-scoped; retention e accesso amministrativo definiti.

### B05 — Segnale urgente e invio report (P0, mobile/backend)
Persistenza di operationId della segnalazione nella bozza o nello stato submission; separare “segnale inviato” da “report ricevuto”. Ripresa dopo crash senza ricreare segnale. Rendere esplicito se una comunicazione urgente è offline e non ancora ricevuta.
Accettazione: tre retry e riavvio producono una sola segnalazione e un solo report; UI non dichiara ricezione della centrale prima della conferma server.

### B06 — Query e statistiche (P1, backend/web)
Baseline di letture e tempi. Sostituire conteggi via lettura completa con aggregazioni indicizzate o proiezioni aggiornate alle mutazioni, valutando il rapporto letture/scritture. Cache tenant-scoped, timestamp dei dati e invalidazione dopo azioni. La mappa carica entro viewport o per pagina; gli archivi mantengono cursori stabili e filtri server.
Accettazione: riduzione letture dashboard ≥70% nel carico di riferimento, stesso risultato dei conteggi verificati, nessuna crescita proporzionale a tutto lo storico per semplice apertura pagina. È un target proposto, non una misura attuale.

### B07 — Archivio allegati (P1, platform/backend)
Usare FIREBASE_STORAGE già supportato come candidato principale, confrontando costi e capacità effettive. Migrare per batch, verificare digest e mantenere lettura dei riferimenti legacy. Eliminare synchronized globale dove non necessario; concorrenza limitata; download streaming autorizzato. Limiti di file e totale report, controlli contenuto/MIME, compressione immagini, cleanup degli upload orfani e retention.
Accettazione: nessuna perdita durante migrazione; file non autorizzati respinti; carichi concorrenti entro memoria prevista; report storici ancora leggibili. A 10.000.000 byte l'adapter Firestore usa fino a 39 chunk oltre ai metadati: è un conteggio strutturale, non una stima della fattura.

### B08 — Notifiche durevoli (P1, backend/web/mobile)
Distinguere inbox persistente, tentativo push, accettazione FCM, lettura e presa in carico. Outbox con backoff/jitter, esito per destinatario, lease protette anche in errore e dead letter. Pulizia token effettiva e logout dispositivo. Il client deduplica per notificationId.
Accettazione: fallimento del 30% dei destinatari non diventa successo totale; retry solo necessario; refresh mostra storico; tocco apre risorsa corretta; una push non è presentata come prova di lettura.

### B09 — API e resilienza (P1, backend/mobile)
DTO tipizzati, validazione e contratto errori con requestId, retryable e dettagli campo. Deadline delle dipendenze, retry solo sicuri e limiti di concorrenza. Normalizzare chiavi rate limit; provare proxy reale e repliche. Proteggere webhook mantenendo allowlist/pinning già presenti.
Accettazione: timeout e quote restituiscono messaggi utili; nessun retry duplicante; log correlabili; contratto vecchio mobile verificato.

### B10 — Reporting tecnico (P1, product/backend/mobile)
Snapshot immutabile di checklist, misure, unità, asset e identità usati nel report. Rifiuto motivato e revisione tracciata. PDF e record strutturato coerenti; firma grafica e verifica integrità descritte senza attribuire valore di firma qualificata non verificato. Per rimozione conservare soft delete, motivo e audit; retention separata.
Accettazione: PDF precedente non cambia al rinominare operatore/antenna o aggiornare checklist; prelievo materiali e approvazione riconciliabili.

## 6. Piano frontend web

Il tema Raycast attuale e la palette restano la base. La qualità commerciale viene da gerarchia, leggibilità, uniformità e fluidità dei flussi.

| ID | Intervento | Criterio di accettazione |
|---|---|---|
| W01 P1 | Consolidare token di spaziatura, tipografia, colori semantici, altezze controlli, stati focus/errore | Un componente button/input/select/table/dialog condiviso; varianti dichiarate |
| W02 P1 | Estrarre logica e markup per dashboard, asset, report, operatori, calendario | Modifica a una feature senza modificare altre; regressioni browser esistenti verdi |
| W03 P1 | Shell professionale: topbar, azienda/ambiente, ricerca, notifiche, account e stato sync | Navigazione da tastiera; nessuna ambiguità tra staging e produzione |
| W04 P1 | Dashboard orientata alle azioni: urgenze, ritardi, report da approvare, rete e magazzino | Indicatori con significato dichiarato, timestamp e drill-down filtrato |
| W05 P1 | Disposizione: presentazione, orario/meteo/località/scorciatoie, statistiche e operatività | Ordine richiesto; coppie di pannelli allineate dove utile; niente vuoti forzati |
| W06 P1 | Centro report: lista compatta, approva/elimina visibili, dettaglio PDF/materiali | Click e tastiera; rimozione motivata; azioni sensibili protette e stato coerente |
| W07 P1 | Asset/mappa: simbolo antenna verde visibile, stato con badge/distinto segnale | Visibilità anche con daltonismo; legenda e selezione bidirezionale mappa/lista |
| W08 P1 | Calendario: padding, header, filtri, selezione e note coerenti | Nessun overflow con nomi lunghi; navigazione touch e tastiera |
| W09 P1 | Tabelle: filtri server, cursor, colonne, azioni, vuoto/errore/caricamento | Filtri mantenuti al ritorno dal dettaglio; niente salto di pagina dopo azione |
| W10 P1 | Refresh selettivo, tab nascosta sospesa, richieste deduplicate | Non sostituire form o spostare focus durante edit; refresh manuale affidabile |
| W11 P1 | Accessibilità: focus, dialog, contrasto, zoom e testo reale | Flussi principali a tastiera, zoom 200%, lettura errori con tecnologie assistive |
| W12 P1 | Sessione e MFA: eliminare prompt nativi, progettare form guidati e recovery | TOTP con QR e reauth; errore chiaro; nessun segreto in log |
| W13 P1 | Sessione web: valutare Firebase persistence gestita o backend con cookie HttpOnly | Decisione documentata; se cookie, protezione CSRF coerente; logout e multi-tab testati |
| W14 P2 | Ricerca unificata, filtri salvati, viste per ruolo | Solo risorse autorizzate; utilità validata con cliente |

Uniformare componenti non significa dare a tutte le card la stessa altezza o costringere ogni controllo nella stessa dimensione. Definire densità compatta e standard; allineare pannelli correlati e mantenere crescita naturale del contenuto.

Le note rapide attualmente richieste come temporanee devono dichiarare la durata “questa sessione”. Per note condivise e operative creare una funzione separata con autore e timestamp. Meteo come servizio di supporto: località scelta persistente, ultimo aggiornamento, fallback; il suo errore non deve impedire l'uso della control room.

Non chiamare “disponibilità della rete” la percentuale di antenne attive senza chiarire il denominatore: è una fotografia dello stato gestionale, diversa dall'uptime misurato nel tempo.

## 7. Piano mobile

| ID | Intervento | Criterio di accettazione |
|---|---|---|
| M01 P1 | Dopo scansione mostrare asset, dati tecnici, note persistenti, incarico e azione principale | Tutto nella prima schermata; nessun dato critico nascosto in passaggi ridondanti |
| M02 P1 | Checklist compilabile direttamente nel report, con avanzamento e voci obbligatorie | Nessuna compilazione doppia; stessa versione nel report/PDF |
| M03 P1 | Check-in GPS: stato atteso, distanza, accuratezza, permessi e retry | Errori comprensibili; policy server rispettata; deroga solo autorizzata e auditata |
| M04 P1 | Invio report a fasi con salvataggio bozza, preview e ricevuta | Distinguere salvato/in coda/ricevuto; chiusura solo dopo conferma prevista |
| M05 P1 | Migrare queue/bozze a database locale transazionale e protezione dati | Migrazione e riavvio senza perdita; chiavi protette; nessun leakage tra account |
| M06 P1 | Sync su avvio, resume, rete disponibile e recupero conflitti | Background documentato e testato; retry non modifica identità; capienza visibile |
| M07 P1 | Estrarre home/report/tools in feature, repository e stato Riverpod | Widget di presentazione separati dalle operazioni; errori e refresh uniformi |
| M08 P1 | Ottimizzare immagini/PDF, memoria e caricamenti | Report realistico su dispositivo medio; nessuna chiusura per memoria |
| M09 P1 | Accessibilità e uso sul campo: touch target, testo grande, sole, mano singola | Schermi piccoli e textScale 2; TalkBack; tastiera non copre azioni |
| M10 P1 | Notifiche: deep link, deduplica, foreground/background/logout | Account e task corretti, anche dopo avvio a freddo |
| M11 P1 | Identità e updater parametrizzati, canali QA/production, versione minima | Firma/versionCode coerenti, upgrade conserva bozze; rollback controllato |
| M12 P1 | Test su Android reale: QR, GPS, foto, firma, FCM, App Check | Evidenza su almeno due dispositivi e rete degradata |
| M13 P2 | Cache pack offline, checklist per tipo impianto, scorciatoie tecnico | Dati offline marcati con freschezza; nessuna promessa di realtime senza rete |

La coda corrente contiene già actorUid e tenantId e l'esecutore differisce azioni di altri account: conservarlo. Il pannello visualizza tutte le azioni se UID nullo nel codice letto; verificare che non sia montabile in tale stato e rendere il filtraggio esplicito. È una verifica mirata, non una fuga di dati dimostrata.

Separare fallimento di rete, sessione da rinnovare e mancata approvazione: l'operatore non deve interpretare una rete assente come un account bloccato. L'accesso offline deve avere una policy precisa e non aggirare revoche o autorizzazioni server.

## 8. Qualità, prestazioni e release

### Prove integrate obbligatorie
1. Due aziende e ruoli diversi: isolamento per dati, allegati, QR, inbox e storico.
2. Web assegna task → mobile riceve → QR → check-in → checklist/report → PDF/foto/firma → web approva → magazzino scalato una volta.
3. Rete tolta prima/durante upload, timeout dopo commit server, chiusura forzata, riavvio e replay.
4. Segnale urgente seguito da errore report e retry: una sola segnalazione.
5. Doppia approvazione concorrente e rimozione mentre approvazione è pendente.
6. Logout A/login B con operazioni offline A ancora presenti.
7. Revoca token, sospensione operatore, cambio ruolo/tenant e dispositivo.
8. FCM parziale, token scaduti e notifiche su avvio a freddo.
9. Upgrade APK sopra versione precedente con bozze e coda; firma sbagliata respinta.
10. Ripristino isolato completo e riconnessione client.
11. Dipendenza meteo indisponibile e quota Firestore: operatività residua e messaggi coerenti.
12. Confronto API/versione distribuita con SHA e artefatto approvati.

Mantenere i test mock per rapidità e isolare i test integrati in staging/emulatori. Integrare integration_test Flutter per flussi su device. Aggiungere Firefox/WebKit ai flussi web essenziali secondo browser del cliente. I test di layout non sostituiscono verifica con contenuti realistici.

### Target iniziali da misurare
| Metrica | Target proposto | Condizioni |
|---|---|---|
| API letture comuni p95 | <800 ms | Backend caldo, regione UE, dataset concordato; upload esclusi |
| Dashboard utilizzabile | <2,5 s | Dispositivo/rete e dataset dichiarati |
| Feedback locale azione | <150 ms | Conferma visiva, distinta dall'esito server |
| Freshness inbox centrale | <10 s | Rete presente; meccanismo misurato |
| Sync dopo rete ripristinata | avvio entro 10 s in foreground | Non tempo totale di tutti gli allegati |
| Errori applicativi server | <0,5% | Esclusi errori utente attesi; osservazione pilota |
| Sessioni mobile senza crash | ≥99,5% | Misurazione campione e durata dichiarati |
| Letture dashboard | -70% rispetto baseline | Stesso scenario e correttezza dati |
| Recovery | RPO ≤24h; RTO ≤4h | Solo dopo prova e accordo cliente |

Carico di collaudo iniziale suggerito: 50 utenti contemporanei, 1.000 asset e 10.000 report, poi adeguare al cliente. Non costituisce capacità già certificata. Misurare p95/p99, heap, letture/scritture, numero/byte allegati, code e retry. Niente test di carico sul servizio produttivo senza ambiente e limiti concordati.

Pipeline: test → artefatto identificato → deploy staging → smoke autenticato → release produzione → smoke dopo deploy → monitor. Render deve usare la stessa revisione approvata; acquisire SHA, timestamp, artifact digest e versione API. Deploy indici/regole compatibili prima del codice che li richiede. Migrazioni dati versionate con dry run, checkpoint e rollback/compensazione.

Versionare toolchain e CLI; azioni di terze parti fissate a revisioni revisionate, scansione dipendenze anche Java/Dart, secret scan, SBOM e licenze. Non aggiornare tutte le librerie insieme senza motivo.

## 9. Preparazione commerciale

Prima della consegna, scegliere licenza/cessione, ambiente dedicato o servizio gestito, canale Android e responsabilità di hosting/assistenza. Sono decisioni di vendita, non nuovi endpoint da implementare indiscriminatamente.

| Deliverable | Contenuto | Prova di completamento |
|---|---|---|
| Provisioning cliente | Account/progetti, dominio, Firebase, Render, ruoli, dati iniziali | Secondo ambiente creato da procedura ripetibile |
| Personalizzazione | Nome, logo, contatti, PDF, mail, appId/brand | Nessun hardcode che interrompe login/update |
| Dati e uscita | Import validato, export strutturato e allegati | Export reimportabile e confronto conteggi/digest |
| Supporto | Owner, canale, gravità incidenti, escalation e finestre | Simulazione incidente con referenti |
| Documentazione | Manuali responsabile/tecnico, amministrazione, backup/release | Utente nuovo completa flusso senza sviluppatore |
| Demo | Azienda con dati sintetici e percorso guidato | Nessun dato reale nel materiale commerciale |
| Contratti e privacy | Ruoli trattamento, GPS/foto/firme, retention, accessi e cancellazione | Revisione con referente competente; nessuna conformità dichiarata senza verifica |
| Licenze | Inventario dipendenze, font, mappe e servizi meteo | Attribuzioni e condizioni di uso commerciale verificate |
| Economia | Costi fissi/variabili, storage, traffico, supporto, margine | Tre scenari di utilizzo e budget/alert |
| Handover | Ownership account, chiavi firma, backup, release e recovery | Cliente/gestore autorizzato può operare senza account personale sviluppatore |

Evitare di promettere disponibilità garantita, manutenzione predittiva, conformità certificata o firma legalmente qualificata basandosi soltanto sul nome dei moduli. L'assistente AI resta funzione opzionale con limiti e supervisione; non è una dipendenza del flusso di sicurezza sul campo.

App Distribution è valido per collaudo; un canale commerciale richiede scelta esplicita tra distribuzione privata gestita, store o APK firmato con aggiornamento governato. Mantenere backup della firma fuori dal repository e verificare la disponibilità dei simboli per l'intera finestra di supporto, non soltanto 30 giorni.

## 10. Sequenza di esecuzione

Le stime sono intervalli di giornate-persona, non promesse di calendario; includono implementazione e QA mirata, escludono attese cliente e verifiche contrattuali esterne.

| Fase | Attività | Dipendenza | Stima preliminare | Gate |
|---|---|---|---|---|
| F0 Baseline | Accessi staging/device, metriche, matrice funzionale e commerciale | Nessuna | 2–3 | Snapshot e criteri approvati |
| F1 Protezione | B01–B05: backup, permessi, audit e recupero workflow | F0 | 8–14 | Nessun P0 aperto |
| F2 Dati e prestazioni | B06–B10: query, allegati, notifiche, API e report | F1 policy/contratti | 9–15 | Carico e failure test verdi |
| F3 Esperienza | W01–W13 e M01–M11, consolidamento a feature | Contratti F1/F2 | 12–20 | Flussi reali e UI accettati |
| F4 Validazione/consegna | E2E, device, recovery, upgrade, manuali e provisioning | F1–F3 | 7–12 | Pilota e release candidate |
| Totale orientativo | Consolidamento commerciale | — | 38–64 | Ristimare dopo F0 |

Per uno sviluppatore: circa 8–13 settimane di lavoro dedicato come ordine di grandezza. Capacità, difetti emersi e scope del cliente possono cambiare significativamente la durata. Non è una previsione ricavata da benchmark del progetto.

Suddividere in PR piccole e verificabili; ogni PR contiene problema, soluzione, criteri e prova. Aggiornare backlog unico comune ai due repository. Ordine iniziale: backup → matrice permessi → invio urgente/report → audit → workflow crash recovery → query dashboard → notifiche → allegati → UI/locale → collaudo consegna. Le attività grafiche preparatorie possono procedere dopo il contratto dei componenti, senza ritardare i blocchi P0.

## 11. Gate di vendita

Il via libera arriva quando:
- Tutti i P0 sono chiusi con prova, non con una nota “implementato”.
- I P1 del flusso venduto sono completati; eventuali eccezioni sono circoscritte e accettate.
- E2E reale web/mobile e prove offline/concorrenza sono riusciti.
- Backup recente e restore completo dimostrati; alert e owner operativi.
- Release identificata su Render e mobile, upgrade e ripristino versione provati.
- Nessuna perdita/duplicazione di report, allegati o consumo magazzino nei casi testati.
- Matrice tenant/ruoli e accessi indiretti verificata.
- UI controllata su contenuti reali, responsive e accessibile nei flussi principali.
- Budget di esercizio misurato, manuali e provisioning disponibili.
- Pilota di almeno due settimane o campione equivalente concordato, con incidenti e feedback registrati.
- Handover di account/chiavi e termini commerciali completato.

Non ampliare la release con AI avanzata, telemetria predittiva, microservizi, billing SaaS o iOS se non sono richiesti dal primo cliente. Inserirli nel backlog P2 con beneficio misurabile.

## 12. Fonti e tracciabilità

### Evidenze operative
- [CI web corrente](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38073647535)
- [CI mobile corrente e distribuzione Firebase](https://github.com/Kevinni05/gestionale_radio/actions/runs/38071759045)
- [Monitor backup stale 21:18 UTC](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38087075824)
- [Monitor backup stale 20:18 UTC](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38083198926)

### Sorgenti principali
Tutti i riferimenti seguenti sono fissati alla baseline. Le linee sono orientative e servono a localizzare le evidenze.
- [DashboardService — letture statistiche](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/src/main/java/com/radiotech/radiotech_backend/service/DashboardService.java#L33)
- [Control room — refresh globale e sessione](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/src/main/resources/templates/control-room.html#L2848)
- [Adapter allegati](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/src/main/java/com/radiotech/radiotech_backend/service/LocalAttachmentService.java)
- [AuditService](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/src/main/java/com/radiotech/radiotech_backend/service/AuditService.java)
- [NotificationService — esiti push](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/src/main/java/com/radiotech/radiotech_backend/service/NotificationService.java#L285)
- [MaintenanceReportService — approvazione](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/src/main/java/com/radiotech/radiotech_backend/service/MaintenanceReportService.java#L603)
- [Role](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/src/main/java/com/radiotech/radiotech_backend/security/Role.java)
- [TenantAccessPolicy](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/src/main/java/com/radiotech/radiotech_backend/security/TenantAccessPolicy.java)
- [Playwright — configurazione](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/playwright.config.js)
- [Backup workflow](https://github.com/Kevinni05/radiotechwebapp/blob/477bebcd6af759ed7f94a2665cbd6068efa39a84/.github/workflows/cloud-backup.yml)
- [Mobile — segnalazione urgente](https://github.com/Kevinni05/gestionale_radio/blob/361efcf99f3df7f2177ba10ead3c7927577a3c00/lib/services/api_service.dart#L417)
- [Mobile — chiusura report](https://github.com/Kevinni05/gestionale_radio/blob/361efcf99f3df7f2177ba10ead3c7927577a3c00/lib/report_closure_screen.dart)
- [Mobile — coda offline](https://github.com/Kevinni05/gestionale_radio/blob/361efcf99f3df7f2177ba10ead3c7927577a3c00/lib/services/offline_sync_service.dart)
- [Mobile — updater](https://github.com/Kevinni05/gestionale_radio/blob/361efcf99f3df7f2177ba10ead3c7927577a3c00/lib/services/android_update_service.dart)
- [Mobile — release parametrizzata](https://github.com/Kevinni05/gestionale_radio/blob/361efcf99f3df7f2177ba10ead3c7927577a3c00/.github/workflows/production-release.yml)

### Documentazione primaria consultata
- [Firestore best practices](https://firebase.google.com/docs/firestore/best-practices): query, indici e dimensioni dati.
- [Firestore aggregations](https://firebase.google.com/docs/firestore/solutions/aggregation): scelta tra aggregazioni in lettura e scrittura.
- [Flutter integration testing](https://docs.flutter.dev/testing/integration-tests): prove integrate su app/device.
- [Flutter performance testing](https://docs.flutter.dev/cookbook/testing/integration/profiling): misurazione dei flussi.

Questo piano è interno e tecnico: le criticità non vanno trasformate in promesse commerciali. La presentazione al cliente deve descrivere solo capacità dimostrate dalla release consegnata.

