# Presenza operatori e sessione persistente

La presenza è separata da autenticazione e notifiche. L'app conserva la sessione Firebase; non richiede un QR a ogni riapertura e non cancella il token FCM quando passa in background. Solo «Esci dall'account» disconnette l'account e rimuove l'associazione delle notifiche.

## Attivazione

1. Nel progetto Firebase `gestionale-radio`, creare Realtime Database in Europa (`europe-west1`) in modalità bloccata, oppure usare un'istanza già dedicata alla presenza.
2. Pubblicare le regole di `database.rules.json` **solo su quella istanza**. Negano l'accesso pubblico; gli account scrivono esclusivamente la propria presenza nell'azienda dei custom claims. Manager e lettori autorizzati leggono la propria azienda.
3. Impostare `FIREBASE_DATABASE_URL` sul servizio Render `radiotechwebapp` all'URL esatto mostrato dalla console, senza indovinarlo. L'endpoint `/api/v1/auth/public-config` espone questo URL pubblico alla web app e al mobile.
4. Distribuire l'app mobile 1.5.4+11003 con la stessa firma Android della versione installata, senza disinstallare o cancellare i dati. La sola pubblicazione del codice non aggiorna l'APK sui telefoni.

Finché URL, database e regole non sono configurati, la pagina mostra «Presenza non disponibile»; non interpreta l'ultimo accesso come prova che l'app sia aperta. Login, notifiche e funzioni operative continuano a funzionare.

## Comportamento

Percorso: `presence/{tenantId}/{firebaseUid}/connections/{sessionId}`. Ogni processo dell'app usa una propria connessione; l'operatore è online se esiste almeno una connessione attiva. Prima di scrivere online il client registra sul server `onDisconnect` per rimuovere esclusivamente la propria connessione e aggiornare `lastSeen` con l'orologio del server.

`resumed` attiva la connessione; `hidden`, `paused` e `detached` la chiudono. `inactive` da solo non indica background: fotocamera, scanner e finestre del sistema possono temporaneamente togliere il focus. Un arresto improvviso o una perdita di rete viene rilevato alla scadenza della connessione lato server, non necessariamente nell'istante della chiusura. Nessuna scrittura periodica su Firestore o replica mediante Cloud Functions.

La web app ascolta Realtime Database solo quando la pagina Operatori è visibile. Cambio pagina, scheda nascosta o logout rimuovono gli ascolti. Lo stato sconosciuto o una connessione interrotta non vengono mostrati come online. Le variazioni aggiornano gli indicatori senza ricreare i selettori di ruolo in corso di modifica.

## Costi e distribuzione

Il traffico contiene solo connessioni e timestamp, senza nomi, email o notifiche. Le regole non aprono alcun dato operativo di Firestore. I consumi vanno verificati nella scheda Usage dell'istanza. Il workflow di produzione mobile resta il flusso firmato esistente; i segreti di firma non sono inclusi nei sorgenti.

In questo intervento sono stati generati i bundle web; non sono stati eseguiti test o compilati APK nell'ambiente locale, privo di Flutter e della chiave di firma. La presenza live richiede i passaggi di attivazione sopra.
