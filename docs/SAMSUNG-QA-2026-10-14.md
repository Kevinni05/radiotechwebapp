# Collaudo Samsung del 14 ottobre 2026

Samsung SM-S731U / Android 16, installazione `com.example.gestionale_radio.qa` separata. Account sintetici e credenziali temporanee fuori da Git; nessun invio agli altri operatori.

La prova FCM **provider → foreground → ricevuta cifrata** è riuscita. Verificate registrazione del token sulla route API effettiva, accettazione FCM e ricevuta nell'archivio Android. La prima prova si era fermata dopo timeout di registrazione: il test ora attende il proprietario del token salvato solo dopo HTTP positivo, prima di dichiarare il ricevitore pronto. Account, profilo e credenziali delle due fixture sono stati rimossi.

Anche **seed → arresto/riavvio → sostituzione APK QA 11004→11005** è riuscito: bozza, coda con chiave idempotente, ricevuta e deduplicazione conservate. Il test termina eliminando i propri dati sintetici di bozza/coda. È una prova QA con firma debug, distinta dall'upgrade commerciale firmato.

## Correzione necessaria alla sessione di dispositivo

Il gateway dell'autorità delegava `createCustomToken` trasmettendo solo l'UID: i campi `radioDeviceId` e `radioMfaVerified` calcolati dal controller venivano persi. La candidata introduce `/internal/device-session`: l'autorità verifica il token originale anche sotto il lock utente, controlla identità/tenant/ruolo attuali, recente autenticazione, eventuale TOTP ancora registrato e assenza di concessioni privilegiate/protette. Deriva l'identificativo dall'installazione; ignora le asserzioni MFA e i privilegi inviati dal chiamante. Solo l'autorità firma i campi della sessione.

Il backend verifica la corrispondenza UID/tenant/dispositivo della risposta prima di registrare il dispositivo. Il secondo fattore ricordato nel token di dispositivo è ammesso soltanto per OPERATOR/VIEWER/CUSTOMER; i ruoli privilegiati continuano a richiedere la verifica interattiva esterna. Le informazioni della sessione non modificano i ruoli globali: [comportamento Firebase dei custom claim](https://firebase.google.com/docs/auth/admin/custom-claims).

Suite dell'autorità: **35 test riusciti**. Backend: **193 test eseguiti**, 114 casi emulatore saltati nel run unitario, zero fallimenti/errori; JAR compilato. Le nuove prove coprono falsi claim del chiamante, UID/tenant diversi, accessi vecchi, account disabilitati/protetti, fattori rimossi e revoca tra verifica iniziale e lock. Sono prove di codice; l'emissione e revoca del token di dispositivo sul percorso live candidato restano da collaudare.

Ordine di distribuzione: autorità con il nuovo endpoint, poi backend che lo utilizza. La versione precedente dell'autorità conserva `/internal/operator/token`; quel percorso non viene usato come fallback per nascondere un errore nella nuova associazione.

## Gate ancora aperti

La prova sopra non chiude T15/T16 dalla dashboard, background/cold start e destinazione corretta nell'app completa. Restano upgrade commerciale firmato, GPS/camera/TalkBack, pilota e campi di consegna. Kevin conferma di usare già la dashboard per assegnare gli incarichi; il collaudo completo deve usare un accesso reale autorizzato e un destinatario QA. Candidata non pubblicata, decisione commerciale No-Go.
