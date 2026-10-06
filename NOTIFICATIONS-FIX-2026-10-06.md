# Notifiche operatori — consegna del 6 ottobre 2026

La schermata mobile legge ora `GET /api/v1/operator/me/notifications` con il token Firebase della sessione. Il backend risolve l'operatore dal suo UID e restituisce solo messaggi personali e broadcast della stessa azienda, ordinati dal più recente, fino a 100 risultati. L'app non usa più la lettura degli allarmi della centrale e il ripiego diretto su Firestore che produceva PERMISSION DENIED. Nessuna regola Firestore è stata allargata o pubblicata per questa modifica.

La nuova schermata mostra titolo, testo e data, con aggiornamento manuale e riprova degli errori. Per le nuove notifiche di assegnazione viene conservato `taskId` nello storico ed è disponibile **Apri incarico**. Le comunicazioni generiche restano nella lista notifiche; per aggiungere un incarico si usa Operazioni. Il modulo web ora chiarisce questa distinzione. Le notifiche vengono registrate anche quando il destinatario non ha token push.

## Verifica e artefatti

Test eseguiti il 5 ottobre: 119 backend ordinari superati (164 rilevati, 45 saltati per emulatori/AI), 44 test con emulatori Firebase superati senza salti, 20 test Chromium superati e 68 Flutter superati; analisi Flutter senza problemi. I nuovi controlli verificano isolamento tra destinatari/aziende, accesso autenticato, salvataggio senza token push, collegamento al task, aggiornamento, riprova e schermo piccolo. Le API nei test browser sono simulate. L'accesso alla nuova lista sul telefono resta da confermare dopo aggiornamento APK.

- APK ARM64 debug: `gestionale_radio/artifacts/RadioTech-S25FE-notifications-test.apk`, 112.502.420 byte, versionCode 2005. SHA256 `CC02EFCC640C4DE031A86C4962E6186959CEFD20476752507A8F7F1938F7587D`. Firma v2 e allineamento ZIP 16 KB verificati il 6 ottobre.
- Backend: `build/libs/radiotech.jar`, ricreato il 6 ottobre, SHA256 `F12CD12CE29833F38D325944C7BF3BA4E9DC1C1443F119CE33BA967A138E4BF7`. Avviato con l'account di servizio esistente tramite `scripts/start-local.ps1`.
- Endpoint iniziale APK: `http://192.168.1.227:8080`. Le impostazioni già salvate nell'app restano prioritarie. Aggiornare l'installazione esistente per conservare la sessione e i report locali.

Log: `.dist/qa-notifications-backend.log`, `.dist/qa-notifications-firestore.log`, `.dist/qa-notifications-web.log`, `.dist/qa-notifications-resume-build.log`; mobile `build/qa-notifications-tests.log` e `build/qa-notifications-apk.log`. Log istanza locale: `.dist/local-backend.log`.

L'API filtra in memoria i documenti letti per azienda, per usare l'indice di uguaglianza già disponibile senza introdurre indici nuovi. Per storici molto grandi serviranno query e paginazione dedicate ai destinatari.
