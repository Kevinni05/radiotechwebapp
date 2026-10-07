## Aggiornamento operativo 1.4.0 / 2018 — 7 ottobre 2026

APK aggiornato e installato sul Samsung collegato. Guida: [AGGIORNAMENTO-OPERATIVO-1.4.md](AGGIORNAMENTO-OPERATIVO-1.4.md). Verificati 98 test mobile, 33 scenari browser (32 + ricontrollo del testo tradotto), test backend/emulatori e prova HTTPS completa di QR, incarichi, allegati e invio report. SHA256 APK `A24AF375E5052B08CBF683CA6A95DECEFF5E91F22DE349709EC36CFFF937EE87`.

Osservato un errore di quota Firestore durante la prima verifica; la ripetizione finale è passata. È stata implementata la distinzione fra quota esaurita (503) e credenziali/sessione errate. Le automazioni attendono 30 minuti prima di riprovare dopo quota esaurita. Il piano cloud non è stato modificato.

# RadioTech — verifica e test gratuito

Aggiornamento del 6 ottobre 2026. Questo documento distingue le verifiche eseguite dalla futura attivazione dei servizi aziendali.

## Ambiente attivo

- Web e API: https://locate-kingston-meter-dodge.trycloudflare.com
- Portale clienti: https://locate-kingston-meter-dodge.trycloudflare.com/portal
- APK di test ARM64 per S25 FE: https://locate-kingston-meter-dodge.trycloudflare.com/assets/downloads/RadioTech-enterprise-pro-test.apk
- Versione Android: 1.3.3, build 2017, ARM64, 115.233.942 byte. Firma e allineamento ZIP a 16 KB verificati; installata sul Samsung S25 FE il 7 ottobre 2026 senza disinstallazione. SHA256: `1ABE1443268B3E55A55D1C87776E67DF1D52B55BEBD0C05361254648ACD27EED`.
- Backend locale con Firebase esistente, allegati privati su disco nel profilo di test, AI Ollama locale e backup cifrati pianificati.
- Il tunnel HTTPS permette l'accesso da Internet: telefono e PC possono trovarsi su reti diverse. Il PC, il backend e il tunnel devono restare accesi. L'indirizzo temporaneo può cambiare al riavvio del tunnel; per la produzione serve il dominio stabile aziendale.

## Calendario su richiesta — 7 ottobre 2026

La versione mobile 1.3.3 mostra nella home un pulsante Calendario e apre il calendario completo in una schermata dedicata solo su richiesta. Data e ora restano visibili sotto il logo. Analisi senza segnalazioni e 5 test calendario/orologio superati; aggiornamento installato con ADB.

## Report e allegati integrati — 6 ottobre 2026

Il Centro report mostra gli allegati fuori dal dettaglio ripiegabile, con anteprime di immagini e PDF, Ingrandisci e Scarica distinti. Il PDF si legge tramite PDF.js 6.4.299 incluso nel progetto, con pagine e zoom; non richiede il lettore del browser o servizi esterni. Gli allegati locali restano protetti dall'API autenticata e dal tenant; i PDF sono renderizzati su canvas, HTML/SVG non vengono eseguiti. Caricamento progressivo, tentativi dopo errori e rilascio delle risorse al refresh/logout. `npm run build:pdf` aggiorna gli asset dalla dipendenza bloccata nel lockfile, includendo licenza Apache 2.0 e font.

La build mobile 1.3.2 corregge il feedback dell'invio e produce il nuovo PDF aziendale con nome antenna, operatore, data/ora, logo, foto e firma. I report già archiviati mantengono il PDF originale e usufruiscono delle nuove anteprime web.

Mobile: analisi senza segnalazioni e 94 test superati. **29 test web superati** (`.dist/report-preview-web-tests.log`). Verifiche browser mirate su allegati privati, revisione, rendering PDF reale, navigazione/zoom, download, ingrandimento, riprova e layout 320/768/1440 px. Evidenze in `.dist/report-preview-web-focused.log`, `.dist/report-pdf-verification.json` e nel progetto mobile `qa-report-preview-tests.log`.

Il collaudo autenticato reale via HTTPS (`.dist/report-preview-https-smoke.log`) ha verificato lettura dei nomi originali, upload/download del PDF valido generato dal mobile, report HTTP 201, ripetizione senza duplicati, stato REPORT_SUBMITTED e presenza nella lista web. Il tool usa incarichi/operatore/antenna temporanei e li rimuove al termine. Il backend e gli asset PDF.js aggiornati sono attivi sullo stesso link Cloudflare.

## Calendario home e orologio — 6 ottobre 2026

La versione 1.3.1 aggiunge alla home mobile un calendario mensile degli incarichi personali, con scelta mese/anno/data, ritorno a oggi, indicatori delle scadenze, ricerca e filtri, dettaglio giornaliero, prossime scadenze e attività senza data. Ogni attività apre il relativo incarico. Data e ora sotto il logo si aggiornano ogni secondo senza ricaricare la home o il server.

Analisi Flutter senza segnalazioni e **91 test superati**; verificati cambio anno, febbraio bisestile, mezzanotte, pausa/ripresa e layout con testo ingrandito a 320/740/1024 px. Log nel progetto mobile: `qa-calendar-analyze.log`, `qa-calendar-tests.log`, `qa-calendar-build.log`. Evidenza della consegna: `.dist/mobile-calendar-verification.json`.

## Correzioni QR e report

### Mobile Signal / Raycast e incarichi — 6 ottobre 2026

L'app mobile condivide ora il linguaggio visivo della web app: Inter incluso nel pacchetto, superfici scure glass, bordo sottile, sfondo animato viola/corallo/blu, geometria del segnale e comandi principali chiari. Le azioni della dashboard e le metriche della stessa griglia hanno dimensioni uguali; il layout reagisce allo spazio disponibile e al testo ingrandito. Tema condiviso anche da report, agenda, notifiche, Enterprise Pro, strumenti, profilo e accesso.

Sul dispositivo era salvato un indirizzo Cloudflare precedente e non più attivo. È stato aggiornato soltanto l'indirizzo del server, conservando autenticazione e bozze. Nelle build di test il server è configurabile anche da Profilo e Incarichi, con verifica della connessione prima del salvataggio. Home e Incarichi utilizzano lo stesso endpoint personale; l'elenco degli incarichi si aggiorna alle notifiche e al ritorno in primo piano. Gli stati precedenti in italiano sono normalizzati; COMPLETED non è più archiviato prima del rapporto.

Verifiche: analisi Flutter senza segnalazioni; **86 test mobile superati**, inclusi flusso di accettazione/trasferimento/check-in/avvio/rapporto, aggiornamento da notifica, report da inviare e layout a 320/430/1024 px con testo ingrandito. Log nel progetto mobile: `qa-raycast-analyze.log`, `qa-raycast-tests.log`, `qa-raycast-build.log`. La compilazione Android ha usato una cache Gradle separata (`--project-cache-dir .gradle/raycast-cli`) perché la cache ordinaria era occupata dal processo dell'editor.

Il controllo autenticato reale via HTTPS ha verificato con dati temporanei elenco personale, accettazione, trasferimento, check-in GPS, avvio e invio del rapporto da IN_PROGRESS: HTTP 201, ripetizione senza duplicati, stato REPORT_SUBMITTED nella lista mobile e rapporto presente nell'elenco web. Verificati anche allegati privati, QR operatore, QR antenna, 19 sezioni Pro e readiness. I dati temporanei di operatore, asset, incarico e rapporto vengono rimossi dal tool al termine. Log: `.dist/mobile-raycast-https-smoke.log`.

Sul S25 FE sono stati verificati avvio con sessione conservata, dashboard, caricamento dei due incarichi attivi e presenza dei comandi Check-in GPS e Compila rapporto. Screenshot privati: `.dist/mobile-raycast-home.png` e `.dist/mobile-raycast-tasks.png`. L'APK aggiornato è disponibile anche dal link di download sopra, verificato con HTTP 200 e dimensione corrispondente.

Il login web attraverso un nuovo Quick Tunnel poteva mostrare «Credenziali non valide» anche quando localhost funzionava: il backend rispondeva 403 con testo «Invalid CORS request», prima della verifica Firebase. Il backend attivo è stato riavviato con l'origine HTTPS corrente in `RADIOTECH_CORS_ORIGINS` e `RADIOTECH_PUBLIC_BASE_URL`. La verifica reale del POST login con Origin pubblico e campi vuoti ora raggiunge il controller e restituisce 401 «Email obbligatoria», sia in locale sia tramite HTTPS, senza effettuare tentativi su password reali. Tre test browser mirati sono passati (`.dist/login-cors-web-tests.log`).

`start-local.ps1` registra privatamente PID, workspace e percorso del service account (non le credenziali). `start-free-test.ps1` usa questa registrazione per aggiornare CORS e base URL dei QR quando genera un nuovo link, riavviando soltanto il backend registrato e verificando salute e instradamento del login. Un backend avviato separatamente richiede configurazione esplicita; lo script non arresta processi non corrispondenti. Il login distingue ora il blocco dell'indirizzo HTTPS e l'indisponibilità del server dalle credenziali errate.

Il primo accesso con un badge di un operatore privo di UID Firebase non usa più l'aggiornamento riservato alle sessioni autenticate. Il collegamento è limitato all'operatore identificato dal badge ancora valido; un badge non può modificare il ruolo di un account amministrativo. Il QR versione 2 include il server HTTPS. La versione mobile di test chiede conferma se il server è diverso; quella aziendale mantiene il server previsto dal rilascio. I badge restano monouso: rigenerarli dopo l'utilizzo.

L'errore «Esiste già un report in attesa con questa chiave» proveniva dalla ripetizione dello staging locale degli allegati. Ora il nuovo tentativo recupera la submission già in coda con PDF, foto, timestamp e chiave originali. Una nuova submission usa una cartella distinta; la chiave è isolata per tenant, autore e intervento. La coda è salvata prima delle richieste di rete e le sue scritture sono serializzate. Dopo la conferma del backend vengono registrata la ricevuta e rimossa la coda prima della pulizia degli allegati. Gli invii contemporanei dello stesso report condividono la richiesta in corso.

La schermata mobile di chiusura ha sezioni per controlli, note, foto, misure, materiali, alert e firma, comandi sempre visibili, stato offline distinto dalla ricezione in centrale, errori visibili e campi protetti quando una submission è in coda. «Riprova invio» riprende la submission originale anche senza rigenerare il PDF o richiedere nuovamente il GPS. La posizione resta richiesta per una nuova chiusura di incarico.

L'ulteriore errore «permesso insufficiente» era nella configurazione dell'archivio allegati: l'operatore aveva REPORT_CREATE, mentre il controller richiedeva REPORT_READ anche per scoprire dove caricare il PDF. Ora la configurazione è consultabile dall'operatore abilitato a creare report e il download è limitato ai suoi allegati. Il viewer conserva la consultazione senza il diritto di upload; nessun accesso anonimo è ammesso. La verifica reale di chiusura comprende ora anche configurazione, upload e download del PDF con il token dell'operatore.

## Revisione del layout web

La variante glass aggiunge pannelli traslucidi, sfondo animato a gradienti, logo con ritorno alla home senza perdere la sessione, copyright Kevin Cagnazzo e Anthony Piccinonno e box allineati nelle griglie. Sono verificati 26 percorsi applicativi/responsive più il controllo dedicato a dimensioni, blur e ritorno home, superato nella riesecuzione corretta. Log `.dist/glass-web-tests.log` e `.dist/glass-home-tests.log`. Rimane attivo il rispetto di `prefers-reduced-motion`.

Il rinnovo successivo adotta il tema RadioTech Signal, ispirato al riferimento Raycast su Refero: antracite, accento corallo, Inter locale, grafica SVG originale, navigazione per gruppi e tema condiviso con il portale clienti. Specifiche in `DESIGN.md`. Suite web: 26 test passati; log `.dist/signal-web-tests.log`. L'app Android conserva la build 2013; questo rinnovo riguarda la web app e il portale clienti.

Enterprise Pro ora separa selezione dell'area, ricerca, comandi e registrazioni. L'editor raggruppa etichette e campi su due colonne, con note a larghezza intera e una colonna sugli schermi piccoli. Le registrazioni usano schede adattive, con dettagli e azioni separati. Sicurezza e accessi distingue autorizzazioni account, MFA personale e guida ai ruoli; il cliente è richiesto e visibile solo per il ruolo Cliente. La casella MFA conserva dimensioni native e non eredita la larghezza degli input di testo. Le sezioni aggiunte dinamicamente precedono il piè di pagina anche in AI, squadra e rischi.

La revisione uniforma pannelli, navigazione, pulsanti e spaziature generali; mantiene il cursore standard, lo sfondo Aurora e il rispetto della preferenza per animazioni ridotte. Il portale clienti conserva un layout compatibile con l'editor condiviso. La verifica specifica controlla campi, pulsanti, schede e assenza di sovrapposizioni nell'editor a 320, 390, 768, 1024 e 1440 px, ricerca senza risultati, cambio ruolo, assegnazione accesso e revoca sessioni. Schermate di verifica in `.dist/layout-pro-1440.png`, `.dist/layout-pro-390.png`, `.dist/layout-access-1440.png` e `.dist/layout-access-390.png`; log della verifica completa in `.dist/layout-web-tests-final.log`.

## Evidenze

- Backend: 133 test regolari passati, 53 test condizionati esclusi da questa esecuzione; i test emulatori sono eseguiti separatamente.
- Firestore/Auth emulatori: 52 test passati, inclusi isolamento tenant, badge monouso, revoca dispositivo e idempotenza.
- Verifica autenticata reale tramite HTTPS pubblico: primo login QR, scambio Firebase, associazione dispositivo e lettura del profilo.
- Invio di una chiusura di prova: HTTP 201; ripetizione con lo stesso payload/chiave restituisce lo stesso ID; il report compare nell'elenco web del responsabile. I dati operativi temporanei del test vengono rimossi.
- Verifica reale: 19 sezioni Pro, QR antenna PNG decodificato e risolto, upload/download allegati privati, AI locale attiva e readiness HTTP 200.
- Test mobile: 78 passati, analisi statica senza segnalazioni. Copertura di recupero PDF/chiave/timestamp dopo interruzione, 20 scritture concorrenti senza perdita della coda, isolamento per autore/tenant e layout a 320 px/tablet con testo ingrandito.
- Test web dopo la revisione del layout: 25 passati in 2,5 minuti, con funzionalità gestionali, tutte le sezioni da 320 a 1920 px, editor Pro, assegnazione/revoca accessi, checkbox MFA, animazioni e riduzione delle animazioni. Il cursore rimane quello standard. Pacchetto backend ricompilato con `bootJar`; risorse aggiornate verificate sul server locale 8080, health UP. L'indirizzo HTTPS temporaneo precedentemente indicato non era raggiungibile durante questa verifica del layout; questa esecuzione non ne conferma la disponibilità corrente.

I log aggiornati sono `.dist/pro-backend-tests.log`, `.dist/pro-firestore-tests.log`, `.dist/pro-authenticated-smoke.log`, `.dist/pro-web-tests.log` e, nel progetto mobile, `artifacts/pro-mobile-analyze.log`, `artifacts/pro-mobile-tests.log`, `artifacts/pro-apk-build.log`.

## Prova utente

1. Installare l'APK aggiornato come aggiornamento della versione esistente, conservando dati e bozze.
2. Aprire la web app sull'indirizzo HTTPS e rigenerare il badge dell'operatore. Se il QR indica un nuovo server, verificarlo e confermarlo sul telefono.
3. Aprire l'incarico, compilare le note, aggiungere misure/foto e inviare il report. Attendere la conferma della centrale; nel web aprire Centro report e aggiornare l'elenco.
4. Provare un invio senza connessione: la schermata deve indicare «Salvato sul telefono», senza dichiararlo ricevuto sul web. Ripristinare Internet e scegliere «Riprova invio», verificando un solo report in centrale.
5. Ripetere il test usando la rete mobile del telefono, lasciando acceso il PC con connessione Internet.

Le verifiche automatizzate e quelle sul backend reale non certificano ogni dispositivo o ogni ambiente possibile. SSO/MFA aziendali, webhook destinatari, monitoraggio esterno e cloud di produzione richiedono attivazione e collaudo con gli account dell'acquirente. Non sono stati attivati servizi cloud a pagamento.
