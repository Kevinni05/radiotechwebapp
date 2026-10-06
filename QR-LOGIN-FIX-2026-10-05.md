# Correzione accesso QR — 5 ottobre 2026

La correzione è attiva sul backend locale, porta 8080. Telefono e PC usano la stessa Wi-Fi; nell'app l'indirizzo è `http://192.168.1.227:8080`. Il login completo sul telefono resta da confermare dall'utente.

## Cause e correzioni

- La rigenerazione chiamava `updateOperator`, che protegge i campi del badge ripristinando il token, la scadenza e lo stato d'uso precedenti. Ora una transazione dedicata sostituisce il token, rinnova la scadenza e azzera entrambi i timestamp d'uso, verificando l'azienda del responsabile. L'aggiornamento ordinario continua a proteggere questi campi.
- Il login consumava il badge prima della preparazione Firebase. Ora valida il QR senza consumarlo, prepara la sessione e poi lo consuma con una seconda verifica atomica. Se la preparazione fallisce, il QR resta disponibile. Se viene revocato o usato nel frattempo, la sessione preparata non viene restituita. Il token corrente viene ricontrollato nella transazione.
- La rigenerazione dalla web app apre automaticamente il nuovo badge dopo l'aggiornamento dell'elenco.
- La prova delle credenziali predefinite locali (`UserCredentials`) fallisce nella firma del custom token. L'account di servizio già disponibile per `gestionale-radio` supera sia la firma sia una lettura diagnostica Firebase Auth. Il backend locale è stato riavviato con quell'account, senza modificare account o badge reali. [Requisiti Firebase per la firma dei custom token](https://firebase.google.com/docs/auth/admin/create-custom-tokens).

## Verifica

- Suite ordinaria: 160 test rilevati, 117 eseguiti, 43 saltati perché richiedono emulatori o il modello AI; zero errori o fallimenti. Include 3 nuovi test su mancata firma, consumo dopo preparazione e revoca concorrente.
- Suite Firebase isolata: 42 eseguiti, zero saltati o falliti. Il nuovo test parte da un badge scaduto e usato, verifica la rigenerazione persistita, la validazione senza consumo, il consumo monouso e il rifiuto della rigenerazione per un'altra azienda.
- Test Chromium aggiornato per profilo/badge: superato, compresa apertura automatica del token nuovo dopo rigenerazione; le API di questo test browser sono simulate.
- Firma Firebase reale: verificata senza mostrare token e senza creare account. Health del backend aggiornato sulla LAN: UP. Sintassi dello script PowerShell e `git diff --check`: superati.
- JAR attivo `build/libs/radiotech.jar`, SHA256 `0E22E8ACDC7C7FEAEC7E65CE4838246364AB123382E8C0C7FDEF7F0A8507E990`. L'app Android non richiede un nuovo APK per questa correzione backend.

Log di verifica: `.dist/qa-qr-fix-all-backend.log`, `.dist/qa-qr-fix-firestore-final.log`, `.dist/qa-qr-fix-web.log`. Log della nuova istanza locale: `.dist/local-backend.log`.

## Riavvio locale e prova sul telefono

Usare `scripts/start-local.ps1 -ServiceAccountPath PERCORSO_DEL_JSON_ESISTENTE`, dopo aver fermato la precedente istanza RadioTech sulla porta 8080. Lo script verifica tipo e progetto delle credenziali, non stampa la chiave e non la copia nel repository. Conservare la configurazione di ambiente esistente, inclusa la Firebase Web API key. Non usare le sole credenziali personali gcloud per questo flusso di firma.

Ricaricare la web app, rigenerare il QR dell'operatore e inquadrare il nuovo badge sul telefono. Se l'accesso fallisce, leggere l'errore e il riferimento della richiesta nel log locale prima di rigenerare nuovamente il badge. La firma e lo scambio sul server non sostituiscono il controllo finale della sessione Firebase sul telefono.
