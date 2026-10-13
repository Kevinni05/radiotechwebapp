# Piano per ulteriori account Capo e protezione degli accessi

Data: 12 ottobre 2026. Stato: proposta di implementazione; nessuna modifica alle identità o alla configurazione cloud è stata eseguita durante questa pianificazione.

## Decisione proposta

Consentire ulteriori account personali con lo stesso ruolo applicativo del capo (`CHIEF_EXECUTIVE`), nello stesso tenant, attraverso invito, verifica di un indirizzo email reale, autenticazione a due fattori e approvazione del proprietario fuori dalla web app principale.

Per l'architettura attuale la soluzione proposta è Firebase Authentication con Identity Platform e MFA TOTP gestita per gli accessi quotidiani, insieme a un servizio separato per l'amministrazione delle identità. Il proprietario accede al canale esterno di approvazione con un'identità cloud distinta, protetta da chiave di sicurezza FIDO2/passkey e con un secondo autenticatore di riserva.

La separazione delle credenziali e dei poteri amministrativi è il requisito principale. Verificare l'email dimostra il controllo della casella; non autorizza una persona a diventare capo. La MFA protegge l'accesso a un account; non limita da sola un server compromesso che possiede credenziali amministrative Firebase.

TOTP non resiste al phishing quanto WebAuthn/FIDO2. Per questo le approvazioni dei privilegi devono usare FIDO2. L'estensione della protezione FIDO2 a tutti i login dei capi, tramite un provider gestito compatibile, è un miglioramento successivo da valutare separatamente; non verrà presentata TOTP come equivalente a una passkey.

## Riscontri nel codice attuale

Questi riscontri derivano dal repository locale. I permessi IAM effettivi, i fattori già registrati e la configurazione Identity Platform di produzione devono ancora essere verificati.

- `CapoService.setCapoRole()` assegna claim privilegiati. `CapoService.isCapo()` contiene anche una promozione automatica quando l'email verificata corrisponde a quella configurata.
- `CapoBootstrapController` espone il bootstrap del capo, protetto da un segreto configurato sul backend.
- `AuthService`, `OperatorService` e `ProIdentityController` modificano identità o claim Firebase. La separazione deve riguardare anche la gestione degli operatori: lasciare un generico potere di modifica utenti al backend consentirebbe di tentare modifiche su un capo.
- `AuthService` e `ProDeviceController` generano custom token. Una chiave di firma accessibile alla web app sarebbe incompatibile con l'obiettivo di impedire impersonificazione ed elevazione dopo una compromissione.
- Esistono già funzioni di registrazione e verifica TOTP in `scripts/auth-client.entry.js`, una sezione «Sicurezza e accessi» e controlli MFA nel filtro Firebase. Questo non dimostra che MFA sia attiva in produzione o obbligatoria per tutti i capi.
- Il filtro accetta anche attestazioni di un dispositivo precedentemente autenticato (`radioMfaVerified`). Per i capi questa modalità non dovrà sostituire una nuova verifica MFA quando è richiesta.
- Il cambio password appena aggiunto usa la riautenticazione REST con password e si ferma quando viene richiesto MFA. Prima di rendere MFA obbligatoria bisogna completare anche questo flusso, insieme al recupero account.
- Gli inviti tenant attuali sono firmati dal backend e non costituiscono un sistema di invito nominativo, monouso e con approvazione indipendente per i capi.

## Confine di sicurezza

La web app principale potrà proporre un invito e leggere il suo stato. Non potrà concedere o aumentare privilegi, modificare password/MFA di altri utenti, firmare token per un UID arbitrario, modificare il registro degli amministratori, né assumere l'identità del servizio che può farlo.

Il servizio identità separato avrà credenziali proprie, distribuzione separata e un registro delle autorizzazioni non scrivibile dall'applicazione principale. Una richiesta dalla web app sarà considerata non attendibile finché non approvata dal proprietario sul canale esterno. Anche il backend compromesso dovrà poter ottenere al massimo una richiesta in attesa, non un'approvazione.

Il canale esterno deve mostrare UID, email verificata, tenant e ruolo esatti. L'approvazione sarà vincolata a questi dati, avrà una scadenza e sarà consumata una sola volta. Cambiare il destinatario o il ruolo invaliderà l'approvazione. Un'approvazione non sarà un segreto riutilizzabile memorizzato nella web app.

Il servizio esterno dovrà rileggere direttamente dal provider lo stato dell'identità e dei fattori, senza fidarsi di un booleano inviato dal browser. La concessione non potrà derivare da documenti operativi modificabili dal backend o da una semplice corrispondenza di email.

Anche le pagine che raccolgono password, codici MFA e operazioni di recupero dei capi saranno ospitate nel servizio di accesso separato, su un'origine HTTPS distinta. La web app principale riceverà il risultato dell'autenticazione attraverso uno scambio monouso verificato; non conserverà i segreti degli autenticatori. Questo riduce l'esposizione delle credenziali al codice dell'applicazione, pur senza eliminare il rischio di phishing di TOTP.

Va rimossa dalla web app ogni credenziale che consenta modifiche amministrative Auth, firma di custom token, impersonificazione del servizio identità, modifica IAM o distribuzione del servizio esterno. Anche le chiavi di service account presenti nell'app richiedono verifica: limitare gli endpoint senza togliere i poteri alle credenziali non realizza questa separazione.

I controlli sul registro privilegiato devono usare IAM e risorse separate, eventualmente un database/progetto dedicato. Le regole Firestore del client non limitano le librerie server amministrative.

**Limite della protezione:** l'obiettivo è impedire la creazione di identità privilegiate e l'impersonificazione tramite il sistema di identità se viene compromessa la web app principale. Un backend interamente compromesso può comunque danneggiare i dati a cui ha accesso o alterare le proprie risposte. Se vengono compromessi anche il servizio identità, il proprietario cloud o la relativa catena di distribuzione, questo confine non tiene. Nessuna promessa di «impossibile da hackerare».

## Esperienza di attivazione

1. Il capo apre «Sicurezza e accessi → Invita un altro capo» e indica nome e indirizzo email reale. Conferma l'operazione dopo una nuova autenticazione con MFA.
2. Viene creata una richiesta in attesa, senza privilegi. Il servizio separato limita frequenza e numero degli inviti e invia il messaggio al destinatario.
3. Il destinatario apre il link su una pagina di attivazione gestita separatamente, verifica la casella e sceglie personalmente la password. Se possiede già un account, deve autenticarsi come quel preciso utente: nessuna sostituzione implicita dell'identità esistente.
4. Il destinatario registra il secondo fattore e completa una verifica effettiva. La password non viene inviata via email, mostrata al capo o salvata nei documenti dell'applicazione.
5. Il proprietario controlla i dati nel canale esterno, si autentica con FIDO2 e approva il conferimento dei privilegi. La password o la sessione del capo nella web app non sono sufficienti per questa approvazione.
6. Il servizio identità verifica tutti i prerequisiti, assegna lo stesso ruolo del capo e crea un profilo personale distinto. Nessuna modifica al profilo del capo esistente.
7. L'utente effettua un nuovo login con email, password e MFA e ottiene gli stessi permessi operativi del capo. Notifiche e registro indipendente documentano l'attivazione.

La conferma finale non deve avvenire con un semplice collegamento approvativo nella mail. La mail è una notifica e una verifica di possesso; l'approvazione richiede l'accesso al canale esterno.

Inviti e approvazioni saranno monouso, revocabili e a scadenza. Proposta iniziale: invito valido 24 ore; dopo l'approvazione finale, operazione di concessione completata entro pochi minuti. Scadenze, uso e concorrenza saranno gestiti atomicamente nel servizio esterno. Gli scanner automatici delle email non devono attivare account aprendo un link.

## Protezione del capo già esistente

- Associare una casella email reale e verificata al medesimo UID Firebase, preservando tenant, dati e autorizzazioni. L'indirizzo dovrà essere fornito dal proprietario prima della migrazione.
- Verificare l'identità del capo tramite l'accesso attuale e il canale esterno; poi rimuovere la promozione automatica basata sull'email e disabilitare il bootstrap web ordinario.
- Completare registrazione MFA e prova di recupero prima di richiedere MFA su tutte le sessioni. Dopo l'attivazione revocare le sessioni precedenti e verificare un nuovo accesso completo.
- Ogni capo usa il proprio account, il proprio autenticatore e il proprio profilo. Nessuna password o segreto TOTP condiviso.
- Migrare il cambio password alla riautenticazione gestita che supporta MFA. Un reset password non deve rimuovere il secondo fattore o riattivare automaticamente un account sospeso.
- Il recupero dopo la perdita del telefono richiede verifiche nel canale esterno e revoca delle sessioni/fattori interessati. Nessun endpoint «disattiva MFA» nella web app principale. Non dare per disponibili codici di recupero nativi Firebase: il metodo concreto va progettato e provato.
- Preparare un accesso di emergenza esterno, protetto e conservato separatamente. Non deve creare un login web permanente senza MFA.

## Pianificazione di implementazione

Le durate sono stime iniziali di lavoro tecnico, non date di rilascio. Richiedono accesso alle configurazioni cloud e una prova in ambiente separato.

| Fase | Lavoro | Criterio per avanzare | Stima |
|---|---|---|---|
| 1. Inventario e disegno | Verificare IAM, credenziali, identità esistenti, MFA, ruoli effettivi, provider email e dipendenze mobile/QR. Definire il proprietario esterno e i costi. | Inventario di ogni percorso capace di modificare identità o firmare token; architettura e recupero documentati. | 1–2 giorni |
| 2. Separazione dei poteri | Preparare servizio identità e registro protetto; spostare gestione utenti, assegnazione claim e firma token; eliminare promozione automatica e bootstrap pubblico. Separare credenziali e distribuzioni. | Le credenziali della web app non possono modificare utenti privilegiati, emettere token arbitrari o impersonare il servizio. | 3–5 giorni |
| 3. Accessi e MFA | Configurare Identity Platform/TOTP; completare pagine di accesso separate, riautenticazione, cambio password e recupero; proteggere il canale esterno con FIDO2. | Nessun accesso capo con sola password, reset o token dispositivo. Accesso e recupero provati. | 2–4 giorni |
| 4. Inviti e approvazione | Implementare pagina, invio email, verifica casella, onboarding senza privilegi e approvazione esterna vincolata all'identità. | Solo email verificata + MFA provata + approvazione valida consentono il ruolo capo. | 3–4 giorni |
| 5. Test di sicurezza | Verificare isolamento, concorrenza, scadenze, revoche, tenant, account esistenti, aggiramenti MFA e compatibilità mobile/QR. | Superati i test applicativi e le prove con le vere credenziali IAM dell'ambiente di test. | 2–3 giorni |
| 6. Migrazione e rilascio | Migrare il capo esistente senza cambiare UID, attivare il primo nuovo capo, revocare vecchie sessioni/chiavi e verificare monitoraggio e recupero. | Entrambi accedono con MFA, hanno i medesimi permessi previsti e il vecchio sistema non può concedere privilegi. | 1–2 giorni |

Totale indicativo: 12–20 giorni di lavoro, esclusi tempi di disponibilità del proprietario, configurazione dei domini/email e attivazione dei servizi. La fase 1 può modificare la stima. Nessuna creazione di ulteriori capi prima del completamento dell'isolamento.

## Verifiche che bloccano il rilascio

- Email verificata, ma nessuna approvazione esterna: zero privilegi.
- App compromessa che inventa richieste o modifica email/UID/tenant/ruolo: impossibile ottenere una concessione valida.
- Credenziali effettive della web app usate direttamente contro Auth, firma token, IAM o registro privilegiato: operazioni di elevazione negate.
- Tentativo di riutilizzare o consumare contemporaneamente invito/approvazione: una sola attivazione.
- Password corretta ma MFA assente, non completata o dichiarata con un booleano artificiale: accesso capo negato.
- Login QR, custom token, dispositivo ricordato, vecchia sessione o endpoint alternativo: nessun aggiramento dei requisiti del capo.
- Revoca o sospensione: accesso negato anche con un token precedentemente emesso. Il servizio esterno e le verifiche di revoca devono concordare sulla validità dell'account.
- Nuovo capo: stesso insieme di permessi effettivi, incluse API, moduli Pro, report, magazzino e gestione operatori; nessun accesso ad altri tenant e nessun `SUPER_ADMIN` aggiuntivo.
- Recupero e modifica email/password/MFA: non concedono privilegi nuovi e non aggirano l'approvazione.
- Audit esterno: identifica richiedente, approvatore, destinatario, ruolo, tempi ed esito senza registrare password, codici o link segreti.

Le prove devono comprendere l'isolamento infrastrutturale: test con mock e controlli sui pulsanti non dimostrano che un backend compromesso sia privo dei poteri amministrativi.

## Prerequisiti e costi

Prima dell'implementazione in produzione servono una casella reale per il capo attuale, l'indirizzo del nuovo capo, un'identità esterna del proprietario con FIDO2 e autenticatore di riserva, e l'accesso per verificare IAM e configurazione Firebase. Il codice esistente non basta per stabilire quali servizi siano già attivi.

La MFA TOTP Firebase richiede Identity Platform. Vanno verificati piano, quote, fatturazione, hosting del servizio separato e recapito delle email. Non si assume che l'intera soluzione sia gratuita; questa pianificazione non attiva servizi a pagamento. Il preventivo concreto è un risultato della fase 1.

Per la prima versione gli account avranno pari permessi nella web app. La capacità di concedere altri privilegi elevati rimane fuori dalla web app per tutti, compreso il capo originale.

## Fonti

- [Firebase: MFA TOTP gestita e prerequisito Identity Platform](https://firebase.google.com/docs/auth/web/totp-mfa).
- [Firebase: assegnazione dei custom claim in un ambiente privilegiato](https://firebase.google.com/docs/auth/admin/custom-claims).
- [Google Cloud: permessi Firebase Authentication](https://docs.cloud.google.com/iam/docs/roles-permissions/firebaseauth).
- [Firebase: le librerie server Firestore usano IAM e aggirano le regole client](https://firebase.google.com/docs/firestore/security/get-started).
- [NIST SP 800-63B-4: autenticazione resistente al phishing e gestione degli autenticatori](https://pages.nist.gov/800-63-4/sp800-63b.html).
- [Google Cloud: prezzi Identity Platform](https://cloud.google.com/identity-platform/pricing).
