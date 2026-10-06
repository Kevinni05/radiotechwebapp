# RadioTech — collaudo del 5 ottobre 2026

Consegna del 6 ottobre: [correzione delle notifiche operatori](NOTIFICATIONS-FIX-2026-10-06.md), con APK, nuovo hash backend e verifiche aggiornate. Il backend locale è stato avviato con questa versione.

Aggiornamento successivo: la [correzione del login e della rigenerazione QR](QR-LOGIN-FIX-2026-10-05.md) è ora attiva sul backend locale. Quel documento contiene il nuovo hash JAR e gli esiti aggiornati; i conteggi e l'hash qui sotto descrivono il collaudo precedente alla correzione QR.

Le modifiche sono nei due progetti locali. Non sono state pubblicate su un server o sullo store. Questa verifica prepara il collaudo finale; non sostituisce una prova con account Firebase, backend di staging e dispositivi reali collegati.

## Funzioni e correzioni

- Web: indicatori del magazzino con griglia responsive, intestazioni che vanno a capo, campi contenuti nei pannelli e distanza fra sezioni consecutive. Footer comune alle viste.
- Le animazioni mostrano i pannelli appena entrano nella finestra: un elenco molto lungo non deve rimanere invisibile aspettando una percentuale di visibilità irraggiungibile.
- Pagine dedicate a operatori e centro report. I report hanno ricerca, filtro della revisione e dettagli di intervento, misure, materiali, checklist e allegati HTTPS.
- Agenda interventi con ricerca, filtri per periodo e operatore, attività senza assegnazione/scadenza ed esportazione della selezione. Carico della squadra con incarichi attivi, scaduti e urgenti; apertura dell'agenda per l'operatore selezionato.
- Approvvigionamenti: proposta di reintegro per gli articoli attivi sotto soglia, ricerca per articolo/fornitore, costi noti separati da quelli mancanti ed esportazione CSV. Non invia ordini. Le quantità in unità diverse non vengono sommate.
- Pulsanti di assegnazione, revisione report e movimentazione scorte bloccati durante l'invio per impedire doppi clic concorrenti.
- Mobile: agenda personale con oggi, prossimi sette giorni, scaduti e attività senza data; ricerca, apertura dell'incarico, aggiornamento e recupero dagli errori di rete.
- Mobile: i report approvati escono dalla coda di revisione. Un incarico aperto dall'agenda/notifica viene mostrato per primo, anche se in revisione o archiviato. Corretti i controlli della checklist Telco.

Il carico è un conteggio di incarichi: non rappresenta ore di lavoro o disponibilità. Il costo proposto di reintegro esclude prezzi mancanti, IVA e trasporto. Le nuove sezioni usano i dati e i permessi delle API esistenti; non aggiungono nuove collezioni o migrazioni.

## Verifiche automatiche

| Verifica | Risultato e perimetro |
| --- | --- |
| Backend ordinario | 114 test eseguiti e passati; 42 esclusi in questa esecuzione perché richiedono emulatori o modello locale. |
| Firestore/Auth | 41 test passati su `demo-radiotech`, nessuno escluso: isolamento tenant, provisioning, QR, ciclo degli incarichi/report, approvazione e inventario atomico, incidenti, competenze, alert e workforce. |
| AI locale reale | 1 test passato con Ollama/Qwen3 4B. Totale Java effettivamente eseguito: **114 + 41 + 1 = 156** test distinti. |
| Regole Firestore/Storage | 9 test passati con emulatori locali. |
| Web | Suite Chromium con 20 scenari: login e barriera HTTP, dati e permessi, incarichi, RF, report, inventario, nuove sezioni/CSV, AI, rinnovo/scadenza della sessione, workforce, tastiera e animazioni. |
| Layout web | Tutte le 16 viste a 320, 390, 768, 1024, 1280, 1440 e 1920 px, con dati popolati e testi lunghi: controllo di overflow, sovrapposizioni fra pannelli/campi e contenimento dei controlli. Prova separata con animazioni attive e 300 report. |
| Mobile | `flutter analyze`: nessun problema. **57 test passati**, comprese agenda, bersaglio delle notifiche, HTTP, isolamento della coda offline, resoconti/allegati, schermate aziendali e strumenti Telco/calcolatrice. |
| Android | APK debug in `gestionale_radio/build/app/outputs/flutter-apk/app-debug.apk`; endpoint predefinito `http://10.0.2.2:8080`. |
| Controlli statici | Sintassi JavaScript, `git diff --check` nei due progetti e audit npm: zero vulnerabilità segnalate. |

Le API gestionali dei test browser sono simulate; pagina, risorse, CSP e risposte non autenticate sono servite dal JAR reale. Le prove Java con emulatori verificano separatamente il comportamento persistente e le autorizzazioni. I test Flutter simulano servizi e periferiche: non dimostrano GPS, fotocamera, upload o push su un telefono reale.

I log web/backend sono conservati in `radiotechwebapp/.dist/qa-2026-10-05/`; quelli mobile in `gestionale_radio/qa-current-*.log`. Le catture del layout sono in `build/reports/web/final-*.png`. I riepiloghi delle esecuzioni Java ordinarie e Firebase sono conservati nella directory QA come `backend-results.json` e `firestore-results.json`, perché Gradle sovrascrive i propri risultati all'esecuzione successiva.

Le ultime prove Firebase hanno usato porte QA isolate (Firestore 14180, Auth 14199, Storage 14299), perché la porta 8080 era occupata da un'altra istanza RadioTech. Nessun servizio preesistente è stato arrestato.

| Artefatto del collaudo | SHA-256 |
| --- | --- |
| `radiotechwebapp/build/libs/radiotech.jar` | `708D97AD7A2AC112E47453A2EA608712083CCBE6A6A00757505C9487F5BFEC5D` |
| `gestionale_radio/build/app/outputs/flutter-apk/app-debug.apk` | `646EF7CCB79EAAD2CC22205726AFC6470B8F5F8C94B747046AC8F73A4D7A2E6C` |

## Preparazione del test finale

1. Avvia il JAR aggiornato `radiotechwebapp/build/libs/radiotech.jar` su un backend di test configurato secondo `DEPLOYMENT.md` e `OPERATIONS.md`: Firebase, tenant/ruoli, Storage, chiave di verifica report e origine HTTPS effettiva. La modalità `app.firebase.enabled=false` serve i test browser ma non permette il ciclo operativo con utenti reali.
2. Apri la web app di quel backend. Installa l'APK aggiornato. Un emulatore Android raggiunge il PC tramite `10.0.2.2:8080`; sul telefono imposta l'indirizzo raggiungibile del backend con **Configura Server Backend** nella schermata di login debug. Un endpoint di esempio non è un server attivo.
3. Prepara un responsabile, un operatore approvato e un account in sola lettura nel tenant di test, più un account separato per la prova di isolamento. Usa un asset di test con coordinate verificate e un articolo di magazzino destinato al collaudo.

## Checklist di accettazione con dati reali

| Area | Prova | Esito atteso |
| --- | --- | --- |
| Accesso | Login valido/errato, rinnovo token, logout e cambio account su web/mobile | Credenziali errate rifiutate, sessione recuperabile e nessun dato dell'identità precedente. |
| Operatori | Creazione/invito, approvazione, ruolo, badge; QR valido, riusato e scaduto | Solo i responsabili modificano; badge monouso; operatore approvato ottiene accesso con token rinnovato. |
| Infrastruttura | Creazione/modifica/ricerca antenna, valori RF, mappa e storico | Dati coerenti dopo ricarica; controlli leggibili; QR seleziona l'asset corretto. |
| Pianificazione | Assegna un incarico con priorità e scadenza; verifica agenda e carico web, incarichi e agenda mobile | Stesso incarico/stato; filtri e CSV corretti; nessuna doppia assegnazione con doppio clic. |
| Campo | Accetta → trasferimento → check-in GPS → lavoro; prova posizione rifiutata e fuori area | Transizioni ammesse coerenti, errori gestibili, controllo della distanza applicato. |
| Report | Checklist, misure, note, materiali, foto, firma/PDF, invio; apri dettagli web | Contenuti e allegati leggibili e associati al task corretto; stato in revisione. |
| Revisione | Rifiuta con motivo e reinvia; approva e ricarica web/mobile | Nota ricevuta, incarico aggiornato e consumo scorte applicato una sola volta. Report approvato assente dalla coda di revisione. |
| Magazzino | Crea/modifica articolo, carico/scarico/rettifica, scorta insufficiente, movimenti, archivia/ripristina | Quantità e storico coerenti; operazione impossibile rifiutata senza aggiornamento parziale. |
| Reintegro | Prova articolo sotto soglia, archiviato, costo mancante e fornitore; esporta filtro | Solo scorte attive pertinenti; valori proposti verificabili; nessun ordine inviato. |
| Comunicazioni | Invio mirato e a squadra, ricezione push in primo piano, sfondo e app chiusa | Destinatari corretti; apertura del task dalla notifica, anche in revisione. |
| Incidenti/competenze | Apertura e transizioni incidente, alert letto/valutato, certificazione con scadenza | Stato persistente; azioni riservate al ruolo ammesso. |
| Squadra | Inizio/pausa/ripresa/fine turno, segnalazione e presa in carico/risoluzione | Versioni e stati coerenti, segnalazioni limitate al tenant/operatore. |
| AI | Chat e contesto facoltativo, errore del provider, indicatore e storico manutentivo | Risposta dal provider configurato; errore esplicito; nessuna probabilità di guasto inventata. |
| Offline | Interrompi rete durante un report, riavvia app, ritenta, cambia account e ritorna | Nessun invio con identità diversa, nessun duplicato e nessuna perdita degli allegati locali. |
| Permessi | Ripeti azioni con viewer e account dell'altro tenant | Operazioni non autorizzate rifiutate anche via API; nessuna lettura/modifica fra tenant. |
| Interfaccia | Desktop/telefono, verticale/orizzontale, tastiera aperta e testo ingrandito | Nessuna sovrapposizione; menu, dialoghi e azioni raggiungibili. Tabelle ampie scorrono al loro interno. |

Registrare modello del dispositivo, versione OS, backend, account/ruolo, passaggi ed eventuale riferimento della richiesta in caso di errore. Nessun dispositivo Android era collegato a questa workstation durante la verifica. iOS richiede macOS/Xcode e provisioning e non è stato compilato su Windows. Firma release, rollout e prove sul server pubblico restano attività separate dal collaudo locale effettuato qui.
