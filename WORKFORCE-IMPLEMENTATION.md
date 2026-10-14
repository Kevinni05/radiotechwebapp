# RADIOTECH — Presenze e turnistica

## Funzioni

Il modulo collega le transizioni esistenti START / BREAK / RESUME / END a sessioni storiche e timbrature originali. La web app offre Presenze e Turnistica; l'app mobile offre Presenze e turni, con badge personale, timbrature, calendario pubblicato, PDF e richieste di correzione.

- Registrazioni atomiche e idempotenti: stato, sessione, evento e ricevuta dell'operazione nella stessa transazione Firestore.
- Le nuove sessioni non cancellano quelle concluse. Secondi cumulativi conservati; le ore mostrate escludono le pause.
- Originali conservati; proposte di correzione e validazioni hanno versioni e audit. Le ore approvate sono separate dalle registrate.
- Coda mobile cifrata, separata per tenant e account, con gli stessi identificativi al retry. Stato locale esplicitamente in attesa. Replay su riapertura/ripresa e ogni minuto mentre la schermata è aperta.
- Timbrature differite accettate entro 72 ore; orari futuri oltre 5 minuti, ordine inverso o versioni concorrenti sono respinti. I conflitti restano nella coda, senza cancellazione automatica. Una coda bloccata richiede riconciliazione con il responsabile prima di nuove timbrature.
- Registro mensile con filtri per operatore, nome, validazione e anomalia, eventi originali ed export CSV.
- Scostamenti: ritardi, uscite anticipate/tardive, durata, entrata/uscita mancante, presenza fuori pianificazione, sessione aperta oltre 24 ore e sincronizzazione tardiva. Non approva automaticamente straordinari o assenze.
- Calendario manuale e generatore CP-SAT: copertura esatta, disponibilità, competenze richieste, notti, ferie, limiti mensili e settimanali, riposi minimi, giorni consecutivi, turni bloccati. Obiettivi: ore desiderate, giorni preferiti, equilibrio notti/weekend e stabilità della bozza.
- Controllo dei calendari dei mesi adiacenti già pubblicati; serializzazione per tenant delle revisioni evita pubblicazioni basate su dati concorrenti.
- Pubblicazione ricontrolla anche il calendario manuale: nessun vincolo obbligatorio viene allentato. Soluzioni valide distinguono FEASIBLE e OPTIMAL; fallimenti non pubblicano turni parziali.
- Snapshot pubblicati immutabili, con numero di revisione; bozze successive lasciano disponibile l'ultima pubblicazione.
- PDF A4 personale prodotto dal backend, con caratteri incorporati, tutte le date del mese, sedi, orari, pause, uscita +1, totali e versione. I campi lunghi vanno a capo; nuove pagine quando necessario.
- Pubblicazione web avvia le notifiche; il pulsante Invio notifiche consente un nuovo tentativo. Esito dispositivi e operatori falliti visibile; dettagli nel Centro notifiche. Un invio FCM non prova la visualizzazione sul telefono.

## Limiti espliciti

Massimo 100 operatori, 8 modelli, 1 turno per operatore per giorno e 20 secondi di ricerca. Le fasce dei modelli sono ripetute sui giorni della settimana; le assenze si configurano per data. L'elenco presenze è limitato a 2000 sessioni per query: oltre il limite va selezionato un operatore. I totali raggruppano le sessioni per il mese di inizio; quelle iniziate prima del mese sono incluse per il confronto dei turni notturni e marcate come esterne al periodo. Il confronto usa l'ultima versione pubblicata, che resta conservata anche dopo successive revisioni. La presenza online dell'app resta distinta dalle timbrature.

La pausa pianificata non ha un orario preciso: nei limiti settimanali dei turni che attraversano il confine della settimana è ripartita proporzionalmente. Le regole sono configurazione aziendale, non un motore completo per buste paga o contratti collettivi. I dati storici anteriori al rilascio non sono ricostruibili dal solo stato corrente preesistente.

## Integrazione e verifiche

Dipendenze backend: OR-Tools 9.15.6755 e PDFBox 3.0.8; Java 21. Il bundle web si ricostruisce con `npm ci` e `npm run build:control-room`.

Nuove collezioni: workforceAttendance, workforceTimeEvents, workforceAttendanceAudit, workforcePlans, workforcePlanGuards, workforcePlanVersions, workforceRosterAudit. Accesso tramite API autenticata, scope tenant e UID verificati; nessuna scrittura diretta dai client. Le Firestore Rules esistenti negano l'accesso alle collezioni non esposte. Distribuire gli indici di firestore.indexes.json prima dei test integrati (`firebase deploy --only firestore:indexes` nel progetto corretto).

Test aggiunti: RosterSolverTest, AttendanceReconciliationTest, AttendanceLedgerEmulatorTest e workforce-module.spec.js. Suite mobile: attendance_clock_test.dart. Il protocollo operativo e il collaudo Samsung sono anche nel repository mobile.

## Passaggio a VS Code

Usare il branch `feat/workforce-attendance-planning` in entrambi i repository. Non fare reset di modifiche locali: eseguire fetch e aprire un worktree separato se necessario. Prima del merge verificare le due CI e distribuire gli indici; testare poi contro backend e app compilati da questi branch.
