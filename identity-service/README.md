# RadioTech Identity Authority

Servizio indipendente che concede il ruolo Capo solo dopo conferma email, MFA TOTP e approvazione del proprietario con WebAuthn/FIDO2. Il proprietario configurato è `kcagnazzo@gmail.com`; gli altri destinatari vengono inseriti nella web app.

Il capo esistente manterrà l'UID `RmaJaJZDYEYIPnrWGfulktNO9vV2` e userà `capo.radiotechwebapp@gmail.com`. L'account operatore già associato a `kcagnazzo@gmail.com` non viene unito, eliminato o promosso automaticamente.

Non distribuire questo servizio con le credenziali, il progetto del registro o la pipeline della web app. Il backend principale non deve possedere poteri di scrittura Firebase Authentication, firma di token, modifica IAM o accesso al registro.

## Configurazione

Variabili obbligatorie:

- `IDENTITY_ORIGIN`: origine HTTPS del servizio, senza percorso.
- `IDENTITY_APP_ORIGIN`: origine HTTPS della web app, diversa dalla precedente.
- `IDENTITY_FIREBASE_PROJECT`: progetto Firebase delle identità esistenti.
- `IDENTITY_REGISTRY_PROJECT`: progetto Google Cloud separato, con Firestore Native, non accessibile all’identità del backend.
- `IDENTITY_FIREBASE_API_KEY`: chiave pubblica del client Firebase.
- `IDENTITY_OWNER_EMAIL=kcagnazzo@gmail.com`.
- `IDENTITY_OWNER_GOOGLE_SUB=101939638982626665189` e `IDENTITY_OWNER_GOOGLE_AUDIENCE=32555940559.apps.googleusercontent.com`: identità e audience del proprietario verificate durante l'inventario Google Cloud. Il servizio verifica crittograficamente il token Google; un token Firebase o un altro utente Google non può sostituirlo.
- `IDENTITY_APP_SECRET`: almeno 32 caratteri casuali; serve per le richieste della web app, non autorizza promozioni.
- `IDENTITY_MAIL_PROVIDER=firebase` (predefinito): email gestita Firebase per impostare la password e continuare nell’invito. Per SMTP personalizzato scegliere `smtp` e configurare `IDENTITY_SMTP_HOST`, `IDENTITY_SMTP_USER`, `IDENTITY_SMTP_PASSWORD`, `IDENTITY_MAIL_FROM`; SMTP TLS sulla porta 465, modificabile con `IDENTITY_SMTP_PORT`.

Usare Secret Manager per il segreto applicativo e le eventuali credenziali SMTP. Firebase Identity Platform deve avere email/password e MFA TOTP abilitati. Autorizzare il dominio del servizio separato. Il provider Google di Firebase non è necessario per il proprietario. La configurazione produzione rifiuta emulatori e un registro nello stesso progetto dell’applicazione.

Il backend principale richiede `RADIOTECH_IDENTITY_ORIGIN`, `RADIOTECH_IDENTITY_APP_SECRET` e `RADIOTECH_IDENTITY_REQUIRED=true`. Non accetta più chiavi private di service account o credenziali di una persona per Firebase in un progetto reale: usare un’identità di servizio senza chiave, con Firebase Auth Viewer e i soli permessi sui dati operativi necessari.

## Primo proprietario e migrazione

1. Distribuire il servizio separato e verificarne `/health`, prima di cambiare l’accesso esistente.
2. L'accesso del proprietario usa l'identità Google Cloud già configurata sul PC, tramite `scripts/open-chief-approvals.ps1`, e successivamente una passkey. Non richiede abilitare Google come provider Firebase, né collegarlo all'account operatore.
3. Da una postazione amministrativa fidata, con ADC autorizzate sul servizio identità e sul registro, eseguire `npm run admin -- owner-setup google-101939638982626665189 PERCORSO_FILE_PROTETTO`. Il file deve essere fuori dal repository e da cartelle condivise. Il launcher legge il file `.radiotech/identity/owner-setup.txt` senza stamparne il contenuto. Registrare due autenticatori distinti entro 30 minuti.
4. Eseguire `npm run admin -- inventory`, poi `npm run admin -- migration-dry-run UID_CAPO capo.radiotechwebapp@gmail.com`. Verificare UID, tenant e conflitti di indirizzo. La nuova casella del capo e l'identità Google del proprietario restano distinte.
5. Se l’email Google esistente è già associata a un UID differente, scegliere una casella reale distinta per il login quotidiano del capo oppure procedere a una migrazione delle identità progettata separatamente. Non cancellare l’account Google o il capo per aggirare il conflitto.
6. Soltanto dopo che servizio e recapito email sono funzionanti, pianificare il passaggio e usare `npm run admin -- migration UID_CAPO EMAIL_VERIFICABILE`. La migrazione sospende il vecchio ruolo, conserva l’UID, cambia email senza marcarla verificata e invia l’invito di attivazione.
7. Il capo conferma email, registra TOTP, effettua un login MFA e completa l’invito. Il proprietario approva con passkey. La concessione ripristina il ruolo `CHIEF_EXECUTIVE` nello stesso tenant e revoca le vecchie sessioni.
8. Distribuire il backend con credenziali limitate e rimuovere/revocare le vecchie chiavi amministrative da Render, CI e postazioni non autorizzate. La prova IAM descritta sotto precede il rilascio.

Il recupero di MFA è disponibile solo nel portale del proprietario con passkey: sospende l’account, revoca le sessioni, rimuove il fattore e manda un invito nominativo vincolato allo stesso UID. L’utente deve riconfigurare MFA e ricevere una nuova approvazione. Un reset password non disattiva MFA.

Una concessione parziale resta sospesa. L’approvazione può essere ripetuta con una nuova verifica WebAuthn per un invito `GRANT_FAILED`. Un arresto durante una modifica dell’identità conserva il blocco `userLocks/UID`; un amministratore esterno deve controllare stato Auth, registro e audit prima di sbloccarlo. Il backend non può rimuovere questi blocchi.

## Prova del confine IAM

Con l’identità effettiva del backend, verificare che siano negate: aggiornamento/creazione/eliminazione amministrativa degli utenti Auth, `signBlob`/`signJwt`, impersonificazione del servizio identità, scritture nel progetto del registro e modifica IAM/distribuzione del servizio separato. Non concedere `Editor`, `Owner`, `Firebase Admin` o `Service Account Token Creator` al runtime.

Controllare anche le autorizzazioni ereditate da cartella/organizzazione e le identità della pipeline. Il solo controllo nel codice non dimostra l’isolamento contro una compromissione completa del backend. La presenza di una chiave privata nel container invalida questa protezione.

## Sviluppo e verifiche

`npm ci`, `npm test`, `npm run build`. I test usano un registro transazionale in memoria e adapter Auth/WebAuthn simulati; i test browser di WebAuthn devono usare anche un autenticatore virtuale e verificare origine, challenge, presenza e verifica utente.

La modalità `IDENTITY_DEMO=true` richiede progetto `demo-*`, entrambi gli emulatori locali e origini `localhost` distinte. Non abilitarla in produzione. I segreti degli inviti vengono conservati soltanto come hash; i link email usano un frammento rimosso dalla pagina appena letto. Le approvazioni sono vincolate a UID/email/tenant/ruolo e consumate una sola volta.

La MFA, la gestione email e il servizio separato richiedono configurazione cloud e verifiche di fatturazione. Il codice non attiva automaticamente la fatturazione, non registra autenticatori al posto della persona e non dichiara verificata una casella senza la conferma del destinatario.
