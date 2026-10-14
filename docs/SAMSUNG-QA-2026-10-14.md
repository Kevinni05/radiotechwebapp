# Collaudo Samsung del 14 ottobre 2026

Samsung SM-S731U / Android 16, installazione `com.example.gestionale_radio.qa` separata. Account sintetici e credenziali temporanee fuori da Git; nessun invio agli altri operatori.

La prova FCM **provider → foreground → ricevuta cifrata** è riuscita. Verificate registrazione del token sulla route API effettiva, accettazione FCM e ricevuta nell'archivio Android. La prima prova si era fermata dopo timeout di registrazione: il test ora attende il proprietario del token salvato solo dopo HTTP positivo, prima di dichiarare il ricevitore pronto. Account, profilo e credenziali delle due fixture sono stati rimossi.

Anche **seed → arresto/riavvio → sostituzione APK QA 11004→11005** è riuscito: bozza, coda con chiave idempotente, ricevuta e deduplicazione conservate. Il test termina eliminando i propri dati sintetici di bozza/coda. È una prova QA con firma debug, distinta dall'upgrade commerciale firmato.

## Correzione necessaria alla sessione di dispositivo

Il gateway dell'autorità delegava `createCustomToken` trasmettendo solo l'UID: i campi `radioDeviceId` e `radioMfaVerified` calcolati dal controller venivano persi. La candidata introduce `/internal/device-session`: l'autorità verifica il token originale anche sotto il lock utente, controlla identità/tenant/ruolo attuali, recente autenticazione, eventuale TOTP ancora registrato e assenza di concessioni privilegiate/protette. Deriva l'identificativo dall'installazione; ignora le asserzioni MFA e i privilegi inviati dal chiamante. Solo l'autorità firma i campi della sessione.

Il backend verifica la corrispondenza UID/tenant/dispositivo della risposta prima di registrare il dispositivo. Il secondo fattore ricordato nel token di dispositivo è ammesso soltanto per OPERATOR/VIEWER/CUSTOMER; i ruoli privilegiati continuano a richiedere la verifica interattiva esterna. Le informazioni della sessione non modificano i ruoli globali: [comportamento Firebase dei custom claim](https://firebase.google.com/docs/auth/admin/custom-claims).

Suite dell'autorità: **35 test riusciti**. Backend: **193 test eseguiti**, 114 casi emulatore saltati nel run unitario, zero fallimenti/errori; JAR compilato. Le nuove prove coprono falsi claim del chiamante, UID/tenant diversi, accessi vecchi, account disabilitati/protetti, fattori rimossi e revoca tra verifica iniziale e lock.

Lo scambio reale del token ha individuato un secondo difetto: Firebase rispondeva `CREDENTIAL_MISMATCH`, perché la firma automatica usava l'account del progetto del registro. La candidata richiede `IDENTITY_TOKEN_SIGNER` appartenente al progetto Auth e lo passa all'SDK. Firmatario dedicato `radiotech-identity-signer@gestionale-radio.iam.gserviceaccount.com`, senza ruoli sulle risorse; soltanto l'autorità indipendente riceve `iam.serviceAccounts.signBlob` sul firmatario tramite ruolo personalizzato. Il runtime web non riceve permessi aggiuntivi. Le prove IAM con l'identità runtime effettiva confermano anche il diniego su questo account. Lo scambio Firebase successivo riesce e restituisce il claim di dispositivo; il Samsung conserva il medesimo dispositivo al riavvio.

Ordine di distribuzione: autorità con il nuovo endpoint, poi backend che lo utilizza. La versione precedente dell'autorità conserva `/internal/operator/token`; quel percorso non viene usato come fallback per nascondere un errore nella nuova associazione.

## Dashboard → Samsung: sei prove riuscite

Accesso dashboard reale effettuato da Kevin con l'account responsabile già autorizzato. Frontend/backend candidati sul computer: tutte le scritture operative API usano Firestore nell'emulatore loopback; Auth, autorità indipendente e FCM sono reali. Nelle prime prove i lettori SDK della Home accedevano ancora alle quattro antenne del tenant reale, in sola lettura. La sonda successiva configura anche questi lettori sull'emulatore, prima della prima chiamata nativa, senza cache Firestore. Il proxy del browser consente invii soltanto alle fixture proprietarie e incarichi soltanto all'antenna fittizia. Il ricevitore avvia l'app completa e verifica scope, associazione API e ricevute cifrate; nessun accesso privilegiato simulato.

| Caso | ID persistente | Esito fisico |
|---|---|---|
| Generale · foreground | `general_e94cb26e9990255ae31db71cf092ecefaeef4ce92b432da51ce4aeef0b5cc078` | Avviso Android, ricevuta cifrata, apertura della comunicazione evidenziata |
| Generale · background | `general_824c4e0dedce3a1968d1e8fbb920fde0e992e8c8309ecc34b28095e2c132c39f` | Avviso Android e apertura della comunicazione corretta |
| Generale · processo terminato | `general_314c82c3b979d4d2d7d2c08f4eb96b17d6cea91a55462044a2c1d2f60479441c` | Riavvio dal tocco, sessione di dispositivo valida, comunicazione corretta |
| Incarico · background | `KrrVVZ2kzFqJyrouH83y` | Incarico disponibile e scheda corretta evidenziata |
| Incarico · foreground | `QCX9Wb6TTIsbPsbAY1Mr` | Ricevuta cifrata, apertura della scheda corretta |
| Incarico · processo terminato | `cTJihfNl6YrPebQd4N9s` | Riavvio dal tocco, incarico corretto e sessione valida |

Tutti e sei gli invii hanno una sola notifica persistente, destinazione QA corretta, `SENT` e un dispositivo accettato da FCM. I tre incarichi sono tre documenti distinti, ciascuno con una sola notifica `task_assignment_ID`. Le ricevute di apertura dei sei ID sono conservate senza duplicati anche dopo i riavvii. Screenshot delle schermate QA ed evidenze correlate sono nel pacchetto privato. Per le prove a freddo il processo QA era assente prima dell'invio: non è stato usato l'arresto forzato delle impostazioni Android.

Autorità QA `radiotech-identity-00006-vuc`, zero traffico ordinario, tag `qa-device`; la revisione precedente `radiotech-identity-00004-nbf` mantiene il 100%. Il backend di produzione continua a usare il codice precedente: le prove riguardano il percorso candidato descritto, non una pubblicazione commerciale.

Alla chiusura del collaudo: logout QA, revoca/eliminazione dei due account Auth sintetici e rimozione delle sole presenze corrispondenti. Tag `qa-device` rimosso, revisione ordinaria `00004-nbf` verificata al 100%; server/browser locali e reverse USB temporanei chiusi. Il PDF della foto è archiviato nelle evidenze private ed eliminato dal telefono; la bozza sintetica resta nell'archivio QA cifrato. Nessuna credenziale della sonda nel pacchetto commerciale.

## GPS sul telefono

Kevin ha autorizzato la posizione precisa e ottenuto l'anteprima con distanza/accuratezza sul Samsung. Prima della conferma il documento è ancora `ACCEPTED`, senza check-in; annullamento della preview effettuato. L'antenna ha coordinate fittizie lontane dal dispositivo. Questa prova verifica sensore, permesso e anteprima, non un check-in dentro geofence o una deroga autorizzata.

## Foto cifrate e aggiornamento QA

Kevin ha rilevato che la fotocamera si apriva, ma l'immagine non compariva. L'anteprima e il PDF leggevano il ciphertext come un'immagine normale. Il mobile ora autentica e decifra in memoria; il passaggio bozza→coda decifra e ricifra per il nuovo percorso, senza copiare un ciphertext con associazione al percorso precedente. Dopo l'aggiornamento QA Kevin ha confermato che la foto già scattata è ricomparsa, senza ripetere lo scatto.

La prova fisica conferma: sorgente cifrata e invariata, PDF generato con la foto, copia per coda cifrata e leggibile. Analisi pulita, **184 test Flutter riusciti**, **8 test nativi CI Linux riusciti** e recovery Android emulato riuscito: [CI del sorgente mobile f304070](https://github.com/Kevinni05/gestionale_radio/actions/runs/38489631207). I due casi allegati falliti in ambiente Windows per il limite preesistente di `File.renameTo` restano un esito distinto. Le etichette di foto e rimozione sono provate automaticamente; Kevin ha scelto di non attivare TalkBack, quindi il collaudo fisico resta aperto.

## Revoca e cambio account

Dal dialog della dashboard il responsabile ha revocato soltanto il dispositivo QA di A. Il record è passato da ACTIVE versione 9 a REVOKED versione 10; il secondo dispositivo della stessa fixture è rimasto ACTIVE. Sul Samsung `/auth/me` ha risposto 401 con «Dispositivo revocato: accedi nuovamente», scope approvato falso e schermata DISPOSITIVO REVOCATO. Una nuova autenticazione reale ha riattivato lo stesso ID, versione 11, con risposta 200 e nuova registrazione FCM. Corretto il mobile che classificava quel 401 come semplice scadenza invece di revoca.

Un invio generale della dashboard a due fixture ha creato **due documenti**, uno per destinatario, nella stessa operazione `3f0b17b4-d619-4574-9273-bcbbd29e9e02`: A `general_c410d2f47cf588a058618b8e0a5de7b875fa0ba1a5f1de2817da9c915e11d5ea`, B `general_a4131e9869737c6e9a288207be27b5c25a231b1de34e4a6100bf8c8c76d33d9f`.

Esito iniziale PARTIAL: A riceve, B NO_TOKENS. Dopo uscita da A e accesso reale B sul medesimo Samsung, le ricevute di A non sono esposte; la inbox mostra soltanto la comunicazione di B. Registrato B, «Riprova push» porta lo stesso documento a SENT: ricezione e apertura fisiche riuscite, senza duplicazione. In totale otto comunicazioni e tre incarichi. È un collaudo sequenziale di due account su un telefono, non una ricezione contemporanea su due dispositivi. L'emulatore locale non è partito per assenza di accelerazione; la CI ha verificato il recovery su Android emulato.

## Regole SDK candidate

Firestore e Storage ora controllano il record ACTIVE, UID e tenant per i token con `radioDeviceId`; token malformati, device assente/revocato o appartenente ad altri sono respinti. La compatibilità dei token legacy senza questo claim è conservata. **17 prove delle regole riuscite**, incluse lettura e scrittura Storage e impossibilità per l'operatore di alterare il registro dispositivi. Regole candidate caricate soltanto nell'emulatore QA, non pubblicate in cloud.

Prova SDK fisica con B e autenticazione reale: dispositivo ACTIVE, API 200, lettura di una sola antenna QA consentita. Dopo impostazione controllata REVOKED del solo record proprietario **nell'emulatore**, API 401 e Firestore SDK `permission-denied`; gli altri due dispositivi restano ACTIVE. Questa verifica SDK è distinta dalla revoca A eseguita tramite dialog della dashboard. [CI web 34dc074](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38495119175) interamente riuscita: autorità, backend, regole/integrations, Chromium/Firefox/WebKit, container e JAR riproducibile.

Per distribuire le regole Storage che consultano Firestore, verificare prima il ruolo Firebase Rules Firestore Service Agent sul service agent Storage, come previsto dalla [documentazione Firebase](https://firebase.google.com/docs/storage/security/rules-conditions). Questa dipendenza non richiede privilegi aggiuntivi al runtime web. Le nuove regole non certificano la revoca immediata dei token legacy né tutte le variazioni di ruolo/account: quei casi restano nel gate integrato.

## Gate ancora aperti

T15 singolo copre i tre stati e multipli copre due account sequenziali; resta la configurazione contemporanea concordata. T16 è riuscito nel percorso candidato sui tre stati; resta la verifica dopo distribuzione governata. Restano upgrade commerciale firmato, flusso completo sul campo, revoca account/cambio ruolo, TalkBack fisico, misure pilota e campi di consegna. Un solo Samsung disponibile. Candidata non pubblicata, decisione commerciale No-Go. Gli APK QA con credenziali temporanee restano fuori da Git e dal pacchetto commerciale.
