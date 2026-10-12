# Consolidamento RadioTech — 12 ottobre 2026

Base effettiva: web `8dcdfe16bdafe443b878a52ddcfce4e9f9d43eb7`, mobile `2a5bac399f6790187b0b57a657dc3cf3c9dd64c9`, entrambi da `origin/main`. Il piano del 10 ottobre resta il riferimento; le correzioni già integrate vengono conservate.

## Modifiche coordinate

| Piano | Aggiornamento | Limite di accettazione |
|---|---|---|
| B06 | Dashboard e monitor interrogano solo SUBMITTED e APPROVAL_PENDING del tenant, in pagine da 500, conservando compatibilità con removedAt mancante, nullo o vuoto. I report rimossi sono esclusi; i report finalizzati non vengono scaricati per contare quelli in attesa. | Altre collezioni dashboard richiedono ancora aggregazioni/proiezioni e misure del target -70%. |
| B08/M10 | La push contiene notificationId, tenantId e recipientUid; i multicast sono separati per account. Il mobile valida identità/tenant prima di mostrare o aprire; ricevute persistenti deduplicano separatamente visualizzazione e apertura. | La finestra è di 200 ricevute per account/canale; non è consegna esattamente una volta illimitata. FCM reale e OS in background richiedono collaudo. |
| B08/M10 | DELETE /api/v1/operator/me/fcm-token rimuove solo la relazione del dispositivo dell'operatore autenticato. Aggiunta/rimozione token avvengono in transazione, preservando altri dispositivi. Logout rimuove relazione, token locale e banner; il cambio identità cancella navigazione privata pendente. | Se logout avviene senza rete, l'invalidazione FCM resta dipendente dal provider. Non attestare cancellazione immediata delle push già mostrate dal sistema. |
| M06 | Coordinatore autonomo per avvio, resume e ripristino rete; debounce, niente richieste sovrapposte e un follow-up quando torna la rete durante un invio. Il timer periodico parte solo in foreground; stop preserva budget delle operazioni non iniziate. | Nessuna sincronizzazione garantita a processo terminato; nessun tempo totale o SLA certificato. |
| M04/M06 | Il retry manuale mostra le operazioni ancora pendenti anziché dichiarare completata una sincronizzazione fallita. | Le ricevute del report restano la prova della conferma server. |

## Compatibilità e distribuzione

1. Pubblicare prima il backend: il mobile precedente continua a leggere le API esistenti e ignora i nuovi campi push.
2. Distribuire poi il nuovo mobile, mantenendo applicationId, firma e archivio Android cifrato già presenti.
3. Una vecchia push senza scope verificabile non apre direttamente una risorsa nel nuovo mobile; lo storico notifiche autenticato resta disponibile. Gli operatori legacy senza firebaseUid non autorizzano un deep link.
4. Nessuna migrazione dati o nuovo indice composito richiesto per queste query: filtri di uguaglianza tenant/status e index merging. La rimozione logica dei report e il relativo audit restano invariati.
5. connectivity_plus è fissato a 7.0.0, compatibile con il dbus delle notifiche correnti. La disponibilità di una rete è un invito al retry, non prova di accesso a Internet. Errori HTTP e backoff restano autoritativi.

## Verifiche ripetibili

Backend: `gradlew test`, `npm run test:firestore`. Test nuovi: dashboard con 1.200 record/paginazione, tenant separati, null/blank e rimozione; età del backlog incluse approvazioni pendenti; relazione token/logout con replay; payload FCM separato per account e retry parziale.

Mobile: `flutter analyze`, `flutter test --reporter expanded --timeout 2m`, compilazione Android QA del workflow. Test nuovi: tre consegne concorrenti, riapertura dell'archivio ricevute, tenant/account errato, dati corrotti preservati, lifecycle/rete, stop e follow-up durante replay, esito manuale ancora pendente, navigazione cancellata al cambio account. fake_async controlla il tempo nei test del coordinatore; non aggiunge attese reali.

## Evidenze operative osservate

- [Backup e restore schedulati](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38108262612): tutti gli step riusciti, inclusi upload cifrato e registrazione del restore. Avvio 11 ottobre 2026, 03:24:14 UTC. Le limitazioni di identità/storage già registrate nel backlog rimangono.
- [Monitor 11 ottobre, 22:19 UTC](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38179940741): health riuscito; operations segnala PENDING_REPORTS, pending=1, backup=OK, metricState=AVAILABLE. L'allarme operativo richiede revisione del report; non viene disabilitato per rendere verde il monitor.

## Collaudo su staging e dispositivi

- Due account e due tenant: ricevere una push per A, passare a B, toccare la vecchia push; nessun task A si apre. Verificare FCM foreground, avvio a freddo e logout offline.
- Tre consegne dello stesso notificationId: un banner e una prima apertura; nuova istanza conserva la deduplica. Nessuna push equivale a lettura o presa in carico.
- Report offline con foto, firma e urgente: ritorno rete, resume, processo interrotto e riavvio; stesso operationId/idempotencyKey e nessun consumo doppio.
- Upgrade firmato sopra la versione precedente con coda, bozze e ricevute; controllare Keystore e foto su almeno due Android reali.
- Dashboard con 1.000 asset/10.000 report: misurare letture, heap e tempi; non eseguire carico sull'ambiente produttivo.

Il [backlog unico](https://github.com/Kevinni05/radiotechwebapp/blob/main/docs/commercial-progress.md) contiene tutti i B/W/M e i gate ancora aperti. Migrazione object storage/streaming/cleanup, aggregazioni delle altre risorse, refactoring completo delle feature, E2E autenticato, IAM/regole effettive, device, carico, provisioning/handover, condizioni commerciali e pilota non sono completati da questa modifica. Il gate di vendita resta aperto.

Riferimenti tecnici: [index merging Firestore](https://firebase.google.com/docs/firestore/query-data/index-overview), [limiti degli eventi di rete](https://pub.dev/packages/connectivity_plus/versions/7.0.0).


## Estensione del consolidamento

Base di questa estensione: web main 98576da, mobile main d2ac0b9. Aggiunti proiezioni idempotenti/backfill e lettore dashboard con flag disabilitato, ricerca indicizzata con verifica del documento corrente, paginazione impianti, streaming autorizzato e verificato, migrazione con checkpoint/rollback e copie legacy conservate, binding atomico degli allegati ai report e pulizia conservativa degli upload nuovi non referenziati. Le cancellazioni degli asset hanno audit atomico; la cancellazione identità operatore persiste intenzione e riconcilia la rimozione del profilo quando Auth risulta già eliminato. Errori globali includono requestId, retryable, code e fieldErrors; Firebase Auth ha timeout di connessione/lettura.

Il controller web è suddiviso in dieci moduli sorgente, con bundle ricostruito anche dal Dockerfile. Barra azienda/ambiente, selezione mappa→lista, query salvate nella sessione e protezione delle note calendario durante l'editing completano i flussi coinvolti. Home/report/tools Flutter sono in feature con repository e provider; checklist, risultati e unità alimentano PDF/API dallo stesso payload immutabile. Ricevute notifiche accettate dall'archivio cifrato nativo; download mobile limitato e vincolato alla sessione; fotografie compresse e limitate a otto da massimo 5 MB.

Canale scelto: APK 1.5.2 build 11000, firmato con il keystore esistente, dal sito HTTPS del proprietario ancora da creare. Il codice 11000 supera la release già distribuita 10074; la precedente preparazione 2023 non va consegnata come aggiornamento. CI e distribuzione privata usano il codice canonico di pubspec.yaml. Non è richiesta un'organizzazione Android Enterprise. La build genera manifest e testo di avviso quando viene fornito un URL valido. Non vengono inviati messaggi ai clienti. Aggiornamenti, digest, certificato e redirect sono verificati anche nel codice nativo.

Collaudo: 149 test Flutter, analisi statica pulita; test reale del database SQLite/Keystore sul Samsung SM-S731U, in installazione QA separata. AVD RadioTech_QA creato sul PC ma avvio impedito dall'accelerazione non disponibile; aggiunto lo stesso test su emulatore Android 34 nella CI. Gli emulatori Firebase verificano contatori, eventi duplicati, spostamento tenant, binding report/allegati, cleanup policy, audit e recovery. L'accesso locale al database Firebase conferma eur3; functions:list sul progetto esistente non riesce. Non sono stati eseguiti carico, migrazione distruttiva o cleanup sulla produzione.

[Procedure, manuali, capacità e consegna](OPERATIONS-AND-DELIVERY.md) nel repository web e [distribuzione privata](https://github.com/Kevinni05/gestionale_radio/blob/main/docs/PRIVATE-APK-DELIVERY.md) nel mobile. Il registro precedente è storico: le voci sopra aggiornano lo stato del codice ma non attestano attivazione delle Functions, collaudo QR/GPS/foto/FCM/App Check, upgrade operativo, benchmark, identità di recovery, pilota o condizioni contrattuali.

## Frontend web — implementazione e collaudo

Le voci seguenti descrivono il codice implementato. Il pilota, il confronto con utenti reali e i benchmark restano criteri di accettazione esterni.

| Voce | Risultato verificabile |
|---|---|
| W01 | Token condivisi, densità dei controlli, target touch, focus, errori per campo e dialoghi riutilizzabili. |
| W02 | Dieci moduli della control room; calendario autonomo; sorgenti e build ripetibile in CI e Docker. |
| W03 | Shell responsive, azienda/ambiente, ricerca, notifiche, account e stato sincronizzazione. |
| W04 | KPI con definizioni e timestamp; scorciatoie per report pendenti, stock basso e incarichi scaduti impostano i filtri di destinazione. |
| W05 | Presentazione, orologio/meteo, statistiche e operazioni ordinati; pannelli responsive con altezza naturale. |
| W06 | Report compatti, approvazione/materiali/PDF, rimozione motivata e protetta; dettagli/espansione conservati dopo refresh. |
| W07 | Simbolo antenna, legenda con testo, stato accessibile, selezione lista/mappa in entrambe le direzioni; refresh conserva zoom. |
| W08 | Calendario isolato, selezione da tastiera, padding e touch; modifica non salvata protetta da cambio giorno/account e aggiornamenti. |
| W09 | Pagine e filtri server per impianti/operatori/magazzino; cursori, errori e conservazione delle pagine già caricate per tutte le liste operative. |
| W10 | Coordinatore selettivo, richieste deduplicate, protezione di editing/focus/visibilità e risposte da sessioni precedenti. |
| W11 | Dialoghi con Escape e ripristino focus, etichette/errori annunciati; prove browser anche a zoom 200%. |
| W12 | Nessun confirm/prompt nativo nei flussi coinvolti; MFA guidata con QR, riautenticazione e recupero già presenti. |
| W13 | Persistenza Firebase gestita, logout e cambi account/multitab protetti dai contratti di sessione esistenti. |
| W14 | Ricerca autorizzata tenant/query con cursore, fallback prima del deploy delle proiezioni, ricerche salvate nella sessione e azioni coerenti con il ruolo. |

Regressioni dedicate: `web-tests/frontend-completion.spec.js`. [CI web finale](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38211287590) riuscita: 123 test su ciascuno di Chromium, Firefox e WebKit, backend, regole/emulatori Firebase, JAR riproducibile e container di produzione. Prove locali: 107 integrazioni backend sugli emulatori senza skip e tre test proiezioni/cleanup. Ricerca e filtri di incarichi/report interrogano anche le pagine successive, con budget limitato e cursori. La personalizzazione mantiene azienda/ambiente nella barra. I browser usano risposte API sintetiche: non attestano MFA/FCM/meteo reali, disponibilità di un servizio esterno o accettazione degli utenti. Le misure di carico e il collaudo operativo rimangono distinti dai test automatici.
