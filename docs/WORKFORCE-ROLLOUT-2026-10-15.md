# Pubblicazione Presenze e Turnistica — 15 ottobre 2026

La [PR #46](https://github.com/Kevinni05/radiotechwebapp/pull/46), integrata in main, è pubblicata sul portale https://radiotechwebapp.onrender.com/dashboard. Il servizio Render rimane il proxy esistente; la nuova applicazione è distribuita nel backend Cloud Run gestionale-radio, europe-west1.

## Versione attiva

- Sorgente applicativo: `3e5abe9fd9a0b7df2893135e556c5273fa4e8e81`, contenuto identico al commit PR `8038381cb06ebecdd7aaa047931f618adeb44d59` verificato dalla [CI riuscita](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38640553409).
- Cloud Build: `e8e53aa1-0d4e-4da1-8571-8535c7f61709`, SUCCESS.
- Immagine: `europe-west1-docker.pkg.dev/gestionale-radio/radiotech/web@sha256:fb7b8cd24aa1a9803d519024388d1b40a3bf96d560e5a25c92d803cb3d561c74`.
- Revisione: `radiotech-web-wf-20261015`, 100% del traffico; tag di anteprima rimosso.
- Identità runtime e variabili/segreti esistenti conservati. Seed e migrazione automatica tenant rimangono disattivati.

## Indici e controlli pubblici

Quattro indici workforce verificati READY: presenze per tenant/data, presenze per tenant/UID/data, eventi per tenant/sessione/versione e recuperi per tenant/data. Sono stati creati soltanto i mancanti; nessun documento o regola modificato dalla preparazione.

Lo script originale riceveva HTTP 400 «Invalid page size. Only 0 is supported». Corretto l'elenco indici per usare il default del servizio e preservare i continuation token: sei test riusciti e verifica reale sul progetto. Il successivo controllo operativo ha inoltre rilevato che la risposta API include indici di altre collezioni: lo script ora filtra il percorso completo del gruppo richiesto e non considera valido un indice estraneo con gli stessi campi. La regressione copre questo caso. L’indice workforceClockRecoveries era stato inizialmente dichiarato READY per questa corrispondenza errata; creazione corretta completata dopo il 500 reale, non omessa dal verbale. Il controllo finale verifica tutti e quattro gli indici della propria collezione READY. Correzione nella [PR #48](https://github.com/Kevinni05/radiotechwebapp/pull/48); non è richiesta per l'esecuzione dell'app, ma per la corretta preparazione amministrativa degli indici.

Dal dominio pubblico Render: health e Firebase HTTP 200/UP; JavaScript e CSS HTTP 200; bundle contiene Presenze, Turnistica e /api/v1/workforce. SHA256 bundle `f606e3f8e34f8535a4ede65f8d6783e9a2b0a428dd10cdd577b3398004035a58`, uguale alla revisione controllata prima della promozione. API roster/members anonima respinta con 401. Nessuna voce ERROR nella verifica iniziale; nel controllo successivo rilevato un 500 su attendance/recoveries, analizzato e riportato qui sotto.

La verifica browser automatica con il profilo di collaudo salvato è stata respinta dalla revisione automatica («blocked by policy»); non ritentata con strumenti alternativi. Richiesta al proprietario verifica manuale, dopo Ctrl+F5, dell'apertura delle due sezioni. Kevin ha confermato che Presenze e Turnistica si aprono entrambe senza errori. Non sono stati creati o pubblicati calendari e timbrature reali per simulare il collaudo.

## Rollback

Baseline precedente conservata privatamente in `.radiotech/cloud/workforce-web-baseline-20261015.json`. Se la nuova revisione ha un guasto confermato, ripristinare il traffico senza cambiare ambiente o dati:

```powershell
gcloud run services update-traffic radiotech-web --project gestionale-radio --region europe-west1 --to-revisions=radiotech-web-00004-5sv=100
```

Conservare gli indici aggiunti; non eliminare registrazioni o calendari eventualmente prodotti dalla nuova versione. Il vecchio codice non offre il nuovo modulo. L'app mobile della PR #31 non viene installata o distribuita da questa pubblicazione web; il collaudo integrato delle timbrature richiede il relativo aggiornamento mobile.

Le altre PR draft (#45, #47 e mobile #30) non sono state integrate da questo rilascio.

Controllo finale: le quattro query del modulo (presenze mensili, presenze del singolo UID, eventi della sessione, timbrature da riconciliare) rispondono tutte HTTP 200 sotto l’identità reale del runtime, in sola lettura. Questo conferma anche la risoluzione della precondizione mancante che aveva causato il 500 su attendance/recoveries. Gli eventi di errore precedenti restano registrati, senza dichiarare retroattivamente assenza di errori. Kevin ha confermato apertura delle due sezioni nel portale pubblico.
