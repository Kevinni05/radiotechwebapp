# Accessi Capo protetti

Il login principale è `capo.radiotechwebapp@gmail.com`. L'account mantiene l'UID precedente e i dati aziendali. L'operatore associato a `kcagnazzo@gmail.com` resta separato; quell'indirizzo Google identifica il proprietario delle approvazioni.

## Uso

1. Nel Centro operativo scegliere **Accesso protetto del Capo** e inserire email, password e codice Authenticator.
2. In **Profilo → Altri profili Capo**, inserire nome ed email reale. Il ruolo predefinito è **Capo: tutti i permessi**; gli altri ruoli operativi disponibili richiedono la stessa approvazione.
3. Il destinatario imposta la password tramite l'email ricevuta, verifica la casella, configura Authenticator, accede nuovamente e completa l'invito.
4. Sul PC del proprietario aprire **RadioTech - Approva accessi** dal desktop. Aggiornare le richieste, controllare email, UID, azienda e ruolo, e scegliere **Approva con passkey**.
5. Il destinatario accede nuovamente dopo l'approvazione. Aprire un link, completare l'email o inviare un invito non concede privilegi.

Gli inviti scadono dopo 24 ore; il capo può revocarli prima dell'attivazione. Per inviare o revocare inviti la verifica MFA deve essere avvenuta negli ultimi cinque minuti: usare **Riconferma la mia identità con MFA**. Le sessioni privilegiate richiedono un nuovo accesso dopo otto ore e non accettano token custom o attestazioni di dispositivi ricordati.

## Password e recupero

**Profilo → Cambia password** apre il servizio indipendente e richiede un accesso recente con MFA. La nuova password deve avere almeno 12 caratteri. Il recupero password via email non elimina MFA.

La sospensione e il recupero di MFA avvengono nel portale del proprietario, con passkey. Il recupero sospende l'accesso, revoca le sessioni, ripristina l'email approvata e richiede nuovamente verifica email, MFA e approvazione. Conflitti di UID/email o modifiche interrotte restano bloccati e richiedono gestione amministrativa esterna.

## Confine di sicurezza

L'autorità gira nel progetto separato `radiotech-id-1051055442339`; conserva proprietario, passkey pubbliche, approvazioni, fattori autorizzati e audit. Il backend operativo in `gestionale-radio` usa un'identità senza chiavi private, Firebase Authentication in sola lettura e accesso ai dati operativi. Non può firmare token, modificare utenti Auth, accedere al registro o distribuire/modificare il servizio indipendente.

Il segreto condiviso permette operazioni limitate sugli account ordinari. Anche una richiesta correttamente firmata non può promuovere un operatore né ottenere token amministrativi per un UID protetto. Ogni account elevato deve corrispondere a un'approvazione attiva, all'email verificata e al fattore TOTP approvato. Le regole client Firestore e Storage obbligano gli accessi privilegiati a passare dall'API.

Questo confine protegge la concessione delle identità amministrative. Una compromissione del backend può comunque colpire i dati operativi a cui il backend è autorizzato ad accedere. La protezione dipende anche dal mantenere separati IAM, credenziali e distribuzione dell'autorità.

## Distribuzione e manutenzione

Render conserva l'URL pubblico e inoltra le richieste al backend keyless su Cloud Run. I segreti di verifica report e inviti tenant sono conservati in Secret Manager con i valori precedenti. Le vecchie credenziali amministrative non devono rimanere utilizzabili sul servizio web o nei backup.

Backup e monitoraggio usano GitHub OIDC, limitato al repository e ai workflow su `main`, con un'identità priva di permessi di modifica Auth. La chiave di cifratura dei backup resta quella esistente. L'autorità viene distribuita dall'ambiente amministrativo del proprietario, senza delegare permessi di distribuzione alla pipeline della web app.

Script amministrativi e dettagli del recupero: [Identity Authority](../identity-service/README.md). Non copiare ADC, codici di bootstrap, chiavi o esportazioni private nel repository o nel container.
