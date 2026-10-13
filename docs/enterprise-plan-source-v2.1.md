# RADIO TECH
## Piano Enterprise Pro di preparazione alla pubblicazione ufficiale

**Versione:** 2.1 · **Data:** 13 ottobre 2026 · **Destinatario:** Kevin / responsabile prodotto  
**Perimetro:** control room web, backend Spring Boot, applicazione Flutter Android, dati Firebase, distribuzione e gestione operativa.  
**Obiettivo:** rilasciare una versione commerciale affidabile, coerente e supportabile, con evidenze verificabili per ogni requisito critico.

---

## 1. Decisione di prodotto

La prossima release deve essere una release di consolidamento. La priorità è rendere impeccabile il percorso quotidiano: assegnazione dell'intervento, ricezione sul telefono, scansione antenna, check-in, checklist, report, approvazione e aggiornamento del magazzino.

La qualità Enterprise Pro richiede quattro risultati insieme:

1. **Affidabilità:** un'interruzione, un doppio click o un retry non perde dati e non duplica operazioni.
2. **Esperienza:** la centrale e il tecnico comprendono cosa sta accadendo e quale azione eseguire.
3. **Governabilità:** accessi, costi, backup, versioni e incidenti sono controllabili.
4. **Consegna commerciale:** installazione, aggiornamento, assistenza e uscita dal servizio hanno procedure ripetibili.

Mantenere Flutter, Spring Boot e Firebase; conservare palette e linguaggio Raycast della web app. Evitare una riscrittura generale o un cambio di framework prima del lancio. Ogni intervento deve risolvere un problema osservabile o chiudere un requisito di rilascio.

## 2. Attendibilità e stato iniziale

Questo documento è una pianificazione aggiornata, **non un nuovo audit del codice o una certificazione della produzione**. È basato sul piano tecnico del 10 ottobre 2026 e sulle richieste successive visibili nella conversazione. Non sono stati controllati in questa sessione i repository aggiornati, i dispositivi o il deployment corrente.

Il piano precedente riportava come baseline web `477bebcd6af759ed7f94a2665cbd6068efa39a84` e mobile `361efcf99f3df7f2177ba10ead3c7927577a3c00`. Queste revisioni sono riferimenti storici: non rappresentano necessariamente le versioni odierne.

Le evidenze precedenti su backup STALE, audit, notifiche, query, ruoli e invio report diventano **verifiche da riaprire**, non difetti attuali presunti. Un elemento già corretto deve essere collaudato e chiuso, senza reimplementarlo.

| Stato del backlog | Significato | Evidenza richiesta |
|---|---|---|
| Da verificare | Non conosciamo lo stato della release corrente | Commit, configurazione e prova mirata |
| Confermato | Il problema è riproducibile oggi | Caso, risultato atteso e risultato effettivo |
| In corso | Modifica avviata | PR e criterio di accettazione |
| Da validare | Implementazione presente | Test integrato e verifica ambiente |
| Chiuso | Requisito soddisfatto nella release candidata | Evidenza collegata alla versione |
| Rinviato | Fuori dalla release | Motivo, impatto e release prevista |

**Segnalazione recente del 13 ottobre 2026:** Kevin riferisce che, in un test manuale recentissimo, notifiche generali e incarichi inviati dalla dashboard web non arrivano nell’app mobile. Il mancato arrivo è un problema osservato dall’utente; causa, versioni coinvolte e punto di interruzione restano da verificare. Il requisito NOTIF-03 è P0 e blocca il rilascio fino al collaudo effettivo.

**Regola:** una CI verde è necessaria ma non basta; ogni requisito critico richiede una prova del comportamento effettivo.

## 3. Priorità e regole di rilascio

| Livello | Definizione | Trattamento |
|---|---|---|
| P0 | Perdita/duplicazione dati, accessi non autorizzati, recovery assente o flusso principale inutilizzabile | Blocca la pubblicazione |
| P1 | Requisito della release: UX, notifiche, prestazioni, aggiornamenti e qualità operativa | Completare prima del lancio; eventuale esclusione richiede impatto circoscritto e motivato |
| P2 | Evoluzione che non serve al primo rilascio | Pianificare dopo il pilota |

La priorità si applica al requisito. Se la verifica iniziale dimostra che è già soddisfatto, il lavoro residuo consiste nel conservare la prova e proteggerlo dalle regressioni.

## 4. Roadmap con gate

Le durate sono finestre preliminari per uno sviluppatore dedicato, da ristimare dopo la verifica iniziale. Sono sequenziali, includono QA mirata e non sono una promessa di consegna.

| Fase | Finestra proposta | Risultato concreto | Gate di uscita |
|---|---|---|---|
| F0 — Baseline e scope | 2–3 giorni lavorativi | Versioni correnti, mappa dei flussi, backlog residuo, staging | Ogni requisito classificato; perimetro 1.0 definito |
| F1 — Sicurezza e integrità | 1–2 settimane | Permessi, recovery, workflow e audit verificati | Nessun P0 aperto |
| F2 — Operatività mobile e centrale | 1–2 settimane | Sessioni, presenza, notifiche, offline e dati efficienti | Percorso completo riuscito anche con guasti simulati |
| F3 — Esperienza Enterprise Pro | 1–2 settimane | Componenti uniformi e pagine complete, responsive e accessibili | Revisione visuale e prove con utenti riuscite |
| F4 — Release candidate | 1 settimana | Build firmate, upgrade, rollback, manuali e monitoraggio | Pacchetto di rilascio riproducibile |
| F5 — Pilota e pubblicazione | 2 settimane di uso reale | Feedback, metriche e correzioni circoscritte | Decisione Go/No-Go documentata |

L'ordine di grandezza è **7–11 settimane con pilota**, se il residuo è compatibile con queste finestre. Il precedente audit stimava 38–64 giornate-persona per il consolidamento completo: non si assume qui che quel lavoro sia stato completato. Se F0 conferma un residuo ampio, ricalcolare il calendario o ridurre lo scope prima di fissare una data pubblica.

Percorso critico: baseline → permessi/recovery → integrità report → sessione/offline/notifiche → collaudo integrato → aggiornamento e rollback → pilota. La preparazione dei componenti grafici può iniziare mentre si consolidano i contratti funzionali.

## 5. Backlog di sicurezza e integrità

| ID / priorità | Lavoro | Responsabilità | Criterio di accettazione |
|---|---|---|---|
| SEC-01 · P0 | Allineare ruoli e capacità tra API, Firebase, web e mobile; verificare anche ENGINEER | Backend + QA | Matrice ruolo × azione × azienda; richieste dirette e riferimenti indiretti non aggirano i permessi |
| SEC-02 · P0 | Verificare isolamento di asset, task, report, foto, inbox, cache e coda locale | Backend + mobile | Due aziende di test non possono leggere o modificare risorse reciproche, anche sostituendo ID o QR |
| SEC-03 · P0 | Provare sospensione account, revoca sessione e cambio ruolo | Backend + mobile/web | Alla successiva verifica online l'account revocato non esegue azioni; comportamento offline e durata massima espliciti |
| DATA-01 · P0 | Riprovare invio urgente/report e stabilità delle chiavi di idempotenza | Mobile + backend | Timeout dopo commit, tre retry e riavvio producono una sola segnalazione e un solo report |
| DATA-02 · P0 | Provare approvazione concorrente, consumo materiali e recovery intermedio | Backend | Due approvazioni simultanee scalano il magazzino una volta; crash tra fasi non lascia task irrecuperabili |
| DATA-03 · P0 | Rendere durevole l'audit delle azioni critiche | Backend | Ruoli, approvazioni, rettifiche stock e rimozioni hanno evento persistente; fallimenti recuperabili e osservabili |
| DATA-04 · P0 | Verificare backup e ripristino completo | Platform + backend | Restore isolato di dati e allegati, controllo conteggi/digest, tempi registrati e procedura eseguibile |
| DATA-05 · P1 | Consolidare versioni e snapshot dei report | Backend + mobile | Report/PDF storico resta coerente dopo modifica di antenna, operatore o checklist |

### Policy da definire prima dell'implementazione

- Il server resta autoritativo su ruoli, stati task, magazzino e approvazioni.
- Ogni invio mantiene la stessa identità tra tentativi; la deduplicazione non dipende dal solo client.
- Una rimozione report conserva motivo e tracciabilità secondo la policy scelta; non deve cancellare la storia delle movimentazioni già avvenute.
- Se si elimina un report approvato, decidere se la rimozione è solo di visibilità o richiede una rettifica separata: mai ripristinare lo stock implicitamente senza prova e audit.
- In caso di conflitto non usare indiscriminatamente “vince l'ultima scrittura”; per report e stock serve una decisione di dominio.
- Target recovery proposto: RPO ≤24 ore e RTO ≤4 ore, da misurare e concordare prima di comunicarlo al cliente.

## 6. Accesso persistente, presenza e notifiche

### 6.1 Accesso mobile: scelta raccomandata

Mantenere l'identità dell'operatore tra le aperture. Se il QR è il mezzo previsto per attivare il dispositivo, richiederlo nell'attivazione iniziale o quando l'accesso deve essere ristabilito; evitare la scansione a ogni avvio.

Separare il QR di attivazione/account dal QR dell'antenna, che serve al flusso tecnico. La chiusura dell'app non equivale al logout. Il logout esplicito e la revoca da centrale devono invece disassociare correttamente il dispositivo dalle notifiche del vecchio account.

| ID / priorità | Intervento | Accettazione |
|---|---|---|
| AUTH-01 · P1 | Ripristino automatico della sessione con stato iniziale chiaro | Avvio a freddo, riavvio telefono e aggiornamento non richiedono QR se l'accesso è valido |
| AUTH-02 · P0 | Gestione dispositivo perso, logout, cambio account e revoca | Nessun dato o push dell'account A diventa visibile a B; accesso revocabile dalla centrale |
| AUTH-03 · P1 | Recovery guidato e lista dispositivi essenziale | Messaggi distinti per rete assente, sessione scaduta e account sospeso; revoca dispositivo tracciata |

### 6.2 Presenza: significato preciso

| Indicatore | Significato | Uso nella scheda Operatori |
|---|---|---|
| App in primo piano | Il tecnico sta usando l'app e il segnale è recente | Badge “App aperta” |
| Connesso in background | Sessione di connessione disponibile, app non in primo piano | Badge “In background”, senza suggerire attenzione attiva |
| Offline | Connessione interrotta o segnale scaduto | Ultima attività con data e ora |
| Stato non aggiornato | Non ci sono prove recenti sufficienti | Stato neutro; nessuna falsa certezza |
| Disponibile / occupato / fuori turno | Stato operativo dichiarato o derivato da regole concordate | Indicatore distinto dalla connessione |

**Architettura candidata:** Realtime Database per le sessioni di presenza, `onDisconnect` per gestire la perdita della connessione, timestamp server e segnali del lifecycle per primo piano/background. Firestore rimane archivio dei dati gestionali. Firebase documenta RTDB con eventuale mirroring tramite Cloud Functions come soluzione per la presenza in Firestore; il mirroring va aggiunto solo se serve ai consumatori [S1].

La centrale può leggere la presenza con regole tenant-scoped oppure tramite un adapter backend autorizzato. F0 deve confrontare questa soluzione con un heartbeat a scadenza nel backend esistente, misurando costi, ritardo e complessità. La preferenza tecnica è RTDB per la gestione delle disconnessioni; non è una garanzia di costo nullo.

**PRES-01 · P1:** sessione separata per dispositivo, identità e azienda autorizzate, protezione da aggiornamenti vecchi, aggregazione su più dispositivi. Testare apertura, background, chiusura, crash, rete persa, cambio account e due telefoni contemporanei. Come target di collaudo, con rete sana il badge cambia entro 10 secondi; in perdita rete non controllata l'interfaccia applica la soglia di freschezza stabilita e mostra l'ultima evidenza. Non promettere offline istantaneo alla terminazione: si rilevano disconnessione e scadenza, non una chiusura infallibile.

**PRES-02 · P1:** selezionare solo le informazioni necessarie. Niente GPS continuo per dimostrare che il tecnico è online; la localizzazione del check-in resta un flusso distinto.

### 6.3 Notifiche affidabili

**NOTIF-01 · P1:** inbox persistente come fonte consultabile; push come avviso. Distinguere creato, tentativo di invio, accettazione del provider, letto e preso in carico. Gli esiti parziali non diventano successo totale. Riprovare solo i destinatari necessari e rimuovere i token non validi.

**NOTIF-02 · P1:** associare il token al dispositivo/account, gestire rinnovo, autorizzazione notifiche, deep link e avvio a freddo. Non inserire informazioni sensibili superflue nel testo della lock screen.

**Accettazione:** notifica assegnazione su app aperta, background e processo terminato nelle condizioni supportate; apertura sul task corretto; deduplicazione; nessuna notifica del vecchio account dopo cambio identità. FCM documenta che dopo l'arresto forzato dalle impostazioni Android occorre riaprire l'app per riprendere la ricezione [S2]. Questa condizione va inserita nel collaudo e nel manuale.

### 6.4 NOTIF-03 · P0 — Mancato arrivo di notifiche generali e incarichi

**Stato:** segnalato dal test manuale recente dell’utente; diagnosi tecnica da eseguire. **Owner:** backend + web + mobile, con collaudo su dispositivo reale. **Dipendenze:** baseline delle versioni distribuite, associazione account/dispositivo e accesso ai log autorizzati.

Riprodurre separatamente due percorsi: (A) invio di una notifica generale dalla dashboard a destinatari selezionati e a tutti gli operatori autorizzati dell’azienda; (B) creazione/assegnazione di un incarico dalla dashboard a un operatore. Per B verificare sia la disponibilità dell’incarico nell’app sia l’avviso push: un incarico presente senza avviso e un incarico assente sono guasti differenti.

| Passaggio da verificare | Prova richiesta | Possibili punti da controllare, senza presumere la causa |
|---|---|---|
| Dashboard → API | Richiesta effettiva, payload, destinatari, risposta e ID operazione | Handler pulsante, endpoint, autorizzazioni, errore nascosto o conferma prematura |
| API → dati persistenti | Notifica/incarico e destinatari registrati nell’azienda corretta | Mapping UID/operatore/tenant, transazione e regole di assegnazione |
| Evento → invio push | Tentativo correlato alla stessa operazione e risultato per destinatario/dispositivo | Trigger/outbox, worker attivo, token presenti, progetto Firebase e credenziali coerenti |
| Provider → dispositivo | Esito provider distinto dalla prova di ricezione sul telefono | Token aggiornato, payload, errori di consegna e configurazione della build |
| Dispositivo → interfaccia | Avviso visibile, inbox aggiornata e incarico consultabile | Permessi, canali Android, handler foreground/background, listener/refresh e filtri account |
| Tocco → destinazione | Apertura della notifica generale o dell’incarico corretto | Deep link, avvio a freddo, sessione ripristinata e autorizzazione risorsa |

Non dichiarare “consegnato” sulla sola risposta HTTP 200 o sull’accettazione FCM. Usare ID correlabili e timestamp server per ricostruire l’intero percorso; non registrare token o credenziali in chiaro. Rendere visibili alla centrale fallimenti totali e parziali, destinatari senza dispositivi registrati e tentativi da riprovare.

**Criteri di accettazione obbligatori:**

- Invio dalla dashboard realmente distribuita alla build mobile candidata installata su almeno due dispositivi: notifica generale singola, generale a più destinatari e assegnazione incarico.
- Prove ripetute con app in primo piano, in background e normalmente terminata, con sessione persistente; registrare la ricezione sul telefono e distinguere questa evidenza dall’esito provider.
- Per ogni incarico: comparsa nella sezione incarichi, dati e assegnatario corretti, avviso visibile e apertura sul dettaglio giusto, senza duplicazioni.
- Per ogni notifica generale: contenuto corretto nell’inbox e avviso di sistema nelle condizioni supportate; nessuna consegna ad account o aziende non destinatari.
- Target proposto in condizioni controllate di rete e dispositivo: arrivo visibile entro 30 secondi; misurare anche il ritardo effettivo. È una soglia di collaudo, non una garanzia di consegna push in tutte le condizioni.
- Con rete temporaneamente assente o permesso notifiche negato: dati persistenti recuperabili al ritorno online/apertura app e stato utile per l’utente; nessuna falsa conferma di avviso visualizzato.
- Rotazione token, logout/login e aggiornamento app non interrompono il flusso; arresto forzato dalle impostazioni verificato separatamente con i limiti documentati in [S2].
- Conservare versione web/API/mobile, ID delle operazioni, timestamp, esiti per destinatario e prova sul dispositivo. Chiudere il P0 soltanto dopo correzione della causa e ripetizione riuscita di entrambi i percorsi.

## 7. Mobile per uso sul campo

| ID / priorità | Miglioramento | Criterio di accettazione |
|---|---|---|
| MOB-01 · P1 | Prima schermata dopo scansione con antenna, stato, misure, note, task e azione principale | Dati essenziali subito visibili; niente doppia navigazione per iniziare il lavoro |
| MOB-02 · P1 | Checklist incorporata nel report | Compilazione unica, progressione chiara, campi obbligatori evidenziati e versione conservata |
| MOB-03 · P1 | Check-in GPS guidato | Stato atteso, distanza e accuratezza comprensibili; permesso negato gestito; deroga autorizzata e tracciata |
| MOB-04 · P0 | Bozze e coda persistenti, protette e separate per account/azienda | App terminata e aggiornamento non perdono dati; migrazione locale verificata; azioni di A non inviate come B |
| MOB-05 · P1 | Invio a fasi con ricevuta server | Distinzione visibile tra bozza, in coda, invio, ricevuto e errore; retry manuale senza duplicazioni |
| MOB-06 · P1 | Ripresa sincronizzazione e conflitti | Trigger su avvio/resume/rete; coda ispezionabile; conflitti risolvibili senza sovrascritture silenziose |
| MOB-07 · P1 | Prestazioni e accessibilità | Foto compresse secondo qualità concordata; uso su dispositivo medio, schermo piccolo, testo ingrandito e TalkBack |

Per il lavoro persistente in background Android, valutare un'integrazione Flutter con WorkManager: Android lo indica come soluzione per lavoro persistente pianificabile [S3]. Verificare i vincoli della libreria adottata e del sistema; non usarlo come promessa di sincronizzazione immediata in ogni condizione. In foreground il target proposto è avvio sync entro 10 secondi dal ritorno della rete, distinto dal tempo totale degli upload.

Salvare la bozza dopo modifiche significative; mostrare ultima persistenza e conteggio allegati. L'operatore deve poter uscire da un form senza perdere lavoro e capire se la centrale ha già ricevuto il report.

## 8. Web: qualità visiva e operativa Enterprise Pro

### Contratto del design system

Le superfici della **Panoramica** diventano il riferimento per tutte le pagine: colore base delle card, bordo, profondità, header e contrasto del testo. Preservare la palette attuale; i colori semantici restano riservati a stato, urgenza ed errore.

Definire un solo set di token per spaziatura, raggi, tipografia, superfici, bordi e focus. Standardizzare button, input, select, chip, dialog, menu e toolbar con due densità dichiarate. Evitare nuovi override specifici per correggere singole pagine.

| ID / priorità | Pagina o componente | Risultato richiesto |
|---|---|---|
| WEB-01 · P1 | Componenti condivisi | Stessi controlli e stessi stati in tutto il prodotto; allineamento senza altezze forzate inutili |
| WEB-02 · P1 | Topbar e navigazione | Gerarchia chiara per azienda, sezione, ricerca, notifiche, account e connessione; menu completo su viewport ridotto |
| WEB-03 · P1 | Panoramica | Urgenze e attività prima dei dati secondari; KPI definiti, freschezza e collegamento ai dettagli; meteo opzionale e non bloccante |
| WEB-04 · P1 | Operatori | Schede professionali con identità, ruolo, stato account, presenza, disponibilità, attività assegnate e ultima attività; filtri e azioni senza passaggio obbligato a vista elenco |
| WEB-05 · P1 | Centro report | Approva/elimina riconoscibili anche in vista compressa quando autorizzati; stato, autore, data, dettaglio e storico; errori contestuali |
| WEB-06 · P1 | Antenne e mappa | Simbolo verde ben visibile come richiesto; stato espresso anche con badge/forma/testo; lista e selezione mappa coerenti |
| WEB-07 · P1 | Calendario e magazzino | Filtri, padding, toolbar e dettagli consistenti; conflitti calendario espliciti e rettifiche stock motivate |
| WEB-08 · P1 | Stati e accessibilità | Vuoto/caricamento/errore/permesso negato distinti; focus visibile, tastiera, zoom 200%, contrasto e dialog verificati |

### Operatori: interazione proposta

La pagina usa una toolbar con ricerca, filtri per stato/ruolo e azione “Nuovo operatore”. Ogni scheda espone le informazioni essenziali e una sola azione primaria coerente con il contesto; un menu secondario offre modifica, dispositivi e azioni amministrative. Un pannello di dettaglio contiene incarichi, cronologia e impostazioni senza uscire dalla pagina.

La presenza è visibile sulle schede, ma non modifica automaticamente la disponibilità. Disattivazione, revoca dispositivi e cancellazione devono avere nomi e conseguenze distinti. Con molti operatori utilizzare caricamento paginato e rendering sostenibile, senza imporre un elenco tabellare.

### Prova visuale di accettazione

Validare con dati realisti: nomi lunghi, 0/1/30 operatori, report con molti allegati, notifiche numerose e testi di errore. Viewport proposti: 1440, 1280, 1024, 768 e 390 px. Nessuna azione essenziale nascosta, nessun overflow accidentale, nessun refresh che sposti focus o cancelli un form.

## 9. Prestazioni, costi e manutenzione

| ID / priorità | Lavoro | Evidenza di chiusura |
|---|---|---|
| PERF-01 · P1 | Misurare query dashboard, polling e paginazione; evitare scansioni dello storico | Conteggi corretti e misure prima/dopo su dataset identico |
| PERF-02 · P1 | Aggiornare solo sezioni visibili, deduplicare richieste e sospendere refresh non necessari | Sessione idle e tab nascosta non generano carico ingiustificato |
| PERF-03 · P1 | Verificare storage allegati, streaming e limiti | Foto/PDF autorizzati, memoria controllata e storici leggibili dopo eventuale migrazione |
| OPS-01 · P1 | Dashboard tecnica e alert azionabili | Errori API, backup, code, fallimenti push e costi hanno soglia, owner e procedura |
| OPS-02 · P1 | Separare staging/produzione e identificare ogni release | Versione web/API/mobile, commit, timestamp e digest riconciliabili |
| MAINT-01 · P1 | Estrarre moduli dalle aree monolitiche modificate | Refactoring mirato a report, operatori e sync con test di dominio; nessuna riscrittura estesa |

### Budget operativo

Misurare tre scenari: piccolo cliente, utilizzo atteso e picco. Per ognuno registrare utenti attivi, aperture dashboard, operazioni per tecnico, report giornalieri, MB allegati, retention e traffico. Separare hosting fisso, database, storage, backup, distribuzione, monitoraggio e supporto.

Per la presenza stimare connessioni contemporanee, eventi per sessione e volume trasferito; evitare un heartbeat Firestore frequente senza modello di consumo. Aggiornare Firestore solo quando esiste un'esigenza gestionale, non per replicare ogni battito della presenza.

Usare soglie di attenzione proposte al 50%, 80% e 100% del budget mensile concordato e un responsabile dell'intervento. Un alert è un segnale di gestione, non va considerato da solo un limite automatico alla spesa. Non attribuire costi numerici prima di verificare configurazione, listini e misure.

### Target proposti, da misurare

| Indicatore | Target iniziale | Condizioni |
|---|---|---|
| API comuni p95 | <800 ms | Servizio caldo, dataset e regione documentati; upload esclusi |
| Panoramica utilizzabile | <2,5 s | Rete e dispositivo concordati; contenuto principale pronto |
| Feedback locale | <150 ms | Conferma dell'azione distinta dalla conferma server |
| Errori server imprevisti | <0,5% | Campione e finestra osservati, errori utente attesi esclusi |
| Sessioni mobile senza crash | ≥99,5% | Riportare numeratore, denominatore e periodo; pilota piccolo non certifica scala |
| Coerenza report/stock | Zero perdite o duplicazioni nei casi di prova | Retry, crash, concorrenza e recovery inclusi |

Carico iniziale candidato: 50 utenti simultanei, 1.000 antenne e 10.000 report, da adattare al cliente. È uno scenario di test, non una capacità già dimostrata. Eseguire le prove in ambiente isolato.

## 10. Collaudo integrato della release candidata

| Caso | Scenario | Esito atteso |
|---|---|---|
| T01 | Web assegna → mobile riceve → QR → check-in → checklist → report → approvazione | Un solo intervento completato, allegati e stock coerenti |
| T02 | Rete assente prima/durante invio; timeout dopo commit | Bozza conservata e replay idempotente |
| T03 | Chiusura forzata/crash/riavvio con coda pendente | Ripresa verificabile senza perdita |
| T04 | Due responsabili approvano contemporaneamente | Un solo effetto di dominio |
| T05 | Eliminazione report in approvazione o già approvato | Policy rispettata, nessuna rettifica implicita, audit completo |
| T06 | Account A → logout → B con bozze di A | Nessuna esposizione o invio tra account |
| T07 | Tenant A tenta ID/allegato/QR di B | Accesso negato in ogni livello autoritativo |
| T08 | Apertura/background/crash/rete persa/due dispositivi | Presenza coerente e informazioni scadute riconoscibili |
| T09 | Push parziale, token invalido, permesso negato, avvio a freddo | Inbox conservata, stato corretto e deep link autorizzato |
| T10 | Revoca account/dispositivo e ruolo cambiato | Permessi aggiornati e flusso recovery chiaro |
| T11 | Upgrade dalla versione precedente con bozze e coda | Firma coerente, dati conservati e API compatibile |
| T12 | Ripristino isolato di dati e allegati | Verifica completezza e RPO/RTO misurati |
| T13 | Quota/timeout dipendenza e meteo indisponibile | Errore utile, funzionalità residue utilizzabili, nessun successo falso |
| T15 · P0 | Dashboard invia notifica generale a singolo e più operatori → ricezione su dispositivi reali | Inbox e avviso verificati, destinatari corretti, esiti correlati; foreground/background/avvio a freddo |
| T16 · P0 | Dashboard assegna incarico → comparsa e push nell’app mobile | Incarico disponibile e avviso ricevuto; deep link corretto, nessun duplicato e nessun successo falso |
| T14 | Tastiera, zoom, testo grande, piccoli schermi | Flussi essenziali completabili senza ostacoli |

Usare almeno due dispositivi Android rappresentativi e i browser realmente previsti dal cliente. Registrare versione OS/app, rete, dati, passi e risultato. Mantenere i test unitari e mock rapidi, aggiungendo E2E su ambiente integrato per i comportamenti che i mock non dimostrano.

## 11. Pubblicazione, supporto e consegna

| ID / priorità | Deliverable | Criterio di completamento |
|---|---|---|
| REL-01 · P1 | Canale Android deciso: privato gestito, store o APK governato | Firma, identità app, aggiornamento e destinatari coerenti; il canale di collaudo non è la sola procedura di consegna |
| REL-02 · P0 | Compatibilità e recovery della release | Backend compatibile con mobile precedente; rollback backend provato; per mobile procedura di build correttiva con versionCode crescente |
| REL-03 · P1 | Controlli di produzione | Segreti, account tecnici, regole, indici e configurazione verificati; nessun seed/debug di test attivo |
| REL-04 · P1 | Manuale responsabile, tecnico e amministratore | Un utente nuovo completa il percorso principale senza lo sviluppatore |
| REL-05 · P1 | Provisioning e uscita cliente | Secondo ambiente configurabile da procedura; export dati e allegati verificato |
| REL-06 · P1 | Pacchetto commerciale e assistenza | Funzioni dimostrate, limiti, responsabilità hosting, canale supporto e gravità incidenti definiti |

Le verifiche relative a privacy, GPS, fotografie, firme, retention e condizioni dei servizi devono essere completate con il referente competente per il modello commerciale scelto. Questo piano non costituisce una valutazione legale. Nessuna conformità o SLA va presentata come acquisita senza evidenza e accordo.

### Runbook di rilascio

1. Congelare scope e identificare release candidate web/API/mobile.
2. Chiudere verifiche critiche e acquisire backup verificato.
3. Eseguire migrazioni e indici compatibili, con dry run e checkpoint.
4. Pubblicare backend/web compatibile; eseguire smoke autenticato.
5. Distribuire mobile firmato a gruppo pilota, verificando upgrade e conservazione dati.
6. Controllare errori, code, notifiche, presenza e costi.
7. Ampliare la distribuzione dopo il gate del pilota.
8. Conservare note di release, artefatti, evidenze e procedure di recupero.

**Trigger di stop:** accesso tra aziende, perdita dati, stock duplicato o crash che impedisce il flusso principale. Sospendere il rollout, preservare evidenze e attivare recovery. Per una regressione visuale circoscritta, valutare impatto senza applicare automaticamente un rollback distruttivo ai dati.

## 12. Pilota e decisione Go/No-Go

Campione proposto: un responsabile e 3–5 operatori rappresentativi, per due settimane. Includere un tecnico poco esperto, un dispositivo medio e situazioni di rete instabile. Se il cliente è più piccolo, adattare il campione conservando i casi di prova.

Raccogliere completamento dei flussi, errori, richieste di aiuto, tempi e feedback. Il pilota deve coprire almeno un ciclo completo di assegnazione/report/approvazione e un upgrade; integrare con guasti simulati in staging i casi che non si verificano naturalmente.

### Checklist del via libera

- [ ] Release e scope identificati; tutte le verifiche riferite alla versione candidata.
- [ ] Nessun P0 aperto e nessun difetto bloccante del percorso principale.
- [ ] P1 del prodotto pubblicato chiusi o esclusi esplicitamente con impatto documentato.
- [ ] Isolamento, ruoli, revoca e cambio account provati.
- [ ] Retry, offline, crash, concorrenza e stock verificati.
- [ ] Backup recente e restore isolato dimostrati.
- [ ] Accesso persistente, presenza e notifiche collaudati su dispositivi reali.
- [ ] NOTIF-03 chiuso: notifiche generali e incarichi inviati dalla dashboard arrivano realmente nell’app; comparsa dei dati, push e deep link verificati separatamente con evidenze.
- [ ] UI uniforme, completa e utilizzabile alle dimensioni previste.
- [ ] Upgrade, compatibilità backend e procedure di recupero provati.
- [ ] Metriche e budget osservati; alert con owner e azione.
- [ ] Manuali, provisioning, assistenza e responsabilità di consegna pronti.
- [ ] Pilota completato con risultati e problemi residui registrati.

Il responsabile prodotto decide il rilascio sulla base di questo registro; nessun ticket chiuso soltanto con “implementato”.

## 13. Prime azioni operative

| Ordine | Azione | Output |
|---|---|---|
| 1 | Rilevare commit e versioni attualmente distribuite | Baseline attuale dei due progetti |
| 1a · urgente | Riprodurre il mancato arrivo di notifiche generali e incarichi dalla dashboard e tracciare ogni passaggio | Causa individuata, correzione e prove T15/T16 su dispositivi reali |
| 2 | Riesaminare i P0 del piano precedente | Elenco corretto / confermato / non verificato |
| 3 | Provare il flusso completo con due account e due aziende | Prima evidenza integrata e difetti riproducibili |
| 4 | Verificare backup e restore | Evidenza recovery e tempi reali |
| 5 | Formalizzare sessione, presenza e token dispositivo | Contratto condiviso web/mobile/backend |
| 6 | Fissare componenti e superfici della Panoramica | Specifica grafica comune, applicata prima a Operatori |
| 7 | Stimare solo il backlog residuo | Calendario realistico e scope congelato |

Ogni ticket deve riportare: ID, priorità, problema, comportamento atteso, owner, dipendenze, criterio di accettazione, ambiente/versione di verifica ed evidenza. I ruoli indicati nel documento descrivono responsabilità, non presuppongono un team già disponibile.

## 14. Evoluzioni dopo il lancio

P2 raccomandati, da ordinare secondo uso reale: filtri salvati e ricerca globale; checklist per tipo impianto; reportistica KPI avanzata; provisioning più automatizzato; gestione multi-cliente condivisa se richiesta; integrazioni con sistemi del cliente.

AI avanzata, telemetria predittiva, iOS, microservizi e billing SaaS richiedono una motivazione commerciale separata. Non devono allargare la release di consolidamento.

## 15. Fonti e tracciabilità

**Fonte progettuale:** “RADIO-TECH-Piano-Miglioramento-Commerciale(1).md”, versione del 10 ottobre 2026, letto integralmente per questa pianificazione. Le sue evidenze tecniche sono storiche e vanno riconfermate. Le richieste successive aggiungono accesso persistente senza QR a ogni apertura, presenza operatori, layout Operatori e uniformità delle superfici rispetto alla Panoramica.

**Fonti tecniche primarie consultate il 13 ottobre 2026:**

- **[S1] Firebase — Build presence in Cloud Firestore:** https://firebase.google.com/docs/firestore/solutions/presence
- **[S2] Firebase — Receive messages in Flutter apps:** https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages
- **[S3] Android Developers — Task scheduling:** https://developer.android.com/develop/background-work/background-tasks/persistent

**Aggiornamento 2.1:** aggiunta la segnalazione manuale del 13 ottobre 2026 sul mancato arrivo di notifiche generali e incarichi, il requisito bloccante NOTIF-03, le prove T15/T16 e il relativo gate di pubblicazione. Nessuna correzione del codice o verifica di consegna è stata eseguita con questo aggiornamento del piano.

Architetture, priorità, soglie, campione pilota e calendario sono proposte per RADIO TECH. Non sono capacità già misurate né garanzie dei fornitori.
