# Verifica di consolidamento — 12 ottobre 2026

Questa evidenza distingue ambiente di test, collaudi automatici e gate commerciali. [Backlog unico B/W/M](commercial-progress.md) e [proposta di costi/handover](COMPANY-COSTS-AND-HANDOVER.md).

## Telefono e pacchetto

Samsung SM-S731U collegato: l'app QA isolata ha conservato bozza cifrata, queue, identità/idempotencyKey, ricevuta e deduplica notifiche dopo force-stop, riavvio e sostituzione APK 11000→11001. Tutte le fasi hanno restituito success=true. Questo test non legge o cancella i dati dell'app commerciale.

APK definitivo: RadioTech 1.5.3+11002, applicationId `com.example.gestionale_radio`, 81.621.016 byte. SHA256 `e26addabb602822baec8a15ecacb0b25b1409442827bcc70418591c01e06867e`; certificato originale `73005990b6bad93b1bb3383121f6dcc2293b3e5e3a60e1ae7eaf09a7d97c4fa4`. Verifica apksigner/aapt riuscita; certificato dell'app installata 1.5.1+10077 uguale. APK/AAB/simboli/manifest esatti sono custoditi fuori da Git; nessuna chiave o password nei repository.

Canale privato sul servizio HTTPS esistente: login e tenant proteggono manifest e APK. Upload prima dei byte, poi puntatore della release in transazione con audit; firma/identità/digest verificati prima della pubblicazione. La release 11002 sostituisce 11001; versionCode 2023 e 11000 non sono pacchetti da consegnare per questa release. Non sono stati inviati messaggi ai clienti. Distribuzione automatica Firebase ai tester disabilitata per il canale PRIVATE_SITE.

## API e dati reali

Fixture con utenti sintetici `@radiotech.invalid` e due tenant QA univoci, poi rimossi. Prima prova completa: backend locale con Firebase reale, QR sul servizio HTTPS esistente; 12 verifiche riuscite:

- Download APK autenticato con digest/byte esatti; manifest del tenant estraneo non disponibile, richiesta anonima respinta.
- QR monouso e rifiuto replay.
- Regole Firestore pubblicate: lettura nel tenant ammessa, lettura tra aziende e scrittura diretta negate.
- Permessi API ruolo/tenant; aggregazioni dashboard con Firebase reale.
- Task assegnato: accept, en-route, check-in con coordinate QA e start.
- PDF generato dal builder Flutter di produzione, upload, streaming e confronto byte.
- Report ricevuto e replay idempotente.
- Doppia approvazione, task finalizzato, un solo consumo di magazzino.
- Snapshot tecnico immutato dopo rinomina asset.
- Rimozione motivata conserva stock e audit.
- Account disabilitato: token emesso in precedenza rifiutato.

Le coordinate sono sintetiche: non attestano il sensore GPS. Le prove API non attestano camera, firma su schermo, FCM fisico o App Check.

Il collaudo iniziale HTTPS ha trovato HTTP500 nel download PDF: cartella di staging non scrivibile dall'utente del container. Correzione Docker e controllo esplicito di scrittura in CI. Il secondo collaudo ha trovato un diniego sul completamento ASYNC della risposta già autorizzata: configurazione di dispatch e test di regressione ora verificano streaming autorizzato e rifiuto della richiesta iniziale anonima.

## Statistiche e confini

Fixture emulatore con 10.010 task (8.000 completati, 2.000 annullati, 10 attivi): aggregazioni coerenti e soli 10 documenti task trasferiti. Riduzione strutturale 99,9% rispetto al trasferimento dell'archivio; non è una misura della fattura totale, delle letture globali o del p95 in produzione. Valori legacy non riconosciuti mantengono il lettore compatibile.

Firebase esistente: billing già abilitato, nessun bucket Storage. Sono state pubblicate regole/indici Firestore; non sono stati attivati nuovi piani, bucket o Functions. Migrazione reale Storage e proiezioni cloud rimangono nella configurazione aziendale futura. Le quote gratuite non costituiscono un limite di spesa.

Il gate di vendita richiede ancora prove sul campo fisico+emulatore (camera/GPS/firma/FCM/App Check/rete degradata), carico cliente e RPO/RTO, recupero identità/chiavi, progetto/intestazioni aziendali, privacy GPS, responsabilità nominative, contratto/handover e pilot concordato. Non viene dichiarato superato dalla CI.

## Esiti finali pubblicati

- [Web PR38](https://github.com/Kevinni05/radiotechwebapp/pull/38) integrata in main `dfb2f2d5705f773e15b6b0beb3731c9e63c73043`. [CI del codice](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38237369499): sette job verdi, 125 Chromium + 125 Firefox + 125 WebKit, backend, emulatori/regole, JAR riproducibile e container. Il controllo di scrittura della cartella privata è passato.
- [Mobile PR28](https://github.com/Kevinni05/gestionale_radio/pull/28) integrata in main `530160992b1a52f301114202f887f17e0df1e0fb`. [CI del codice](https://github.com/Kevinni05/gestionale_radio/actions/runs/38237363002): analisi/150 test, compilazione/test nativi e persistenza su Android 34 emulato riusciti. L'ultimo commit della PR corregge esclusivamente una frase della documentazione sul billing.
- Render ha pubblicato la revisione web indicata sopra; health e readiness UP. Dashboard HTTP200 con etichetta COLLAUDO confermata; il profilo runtime resta production. Non è stato creato un ambiente Firebase separato.
- Alle 11:02:49 UTC il flusso completo sul servizio `https://radiotechwebapp.onrender.com` ha superato tutte le 12 verifiche elencate sopra, inclusi APK e PDF byte-per-byte. Fixture e identità QA rimosse al termine.
- Alle 11:03:34 UTC prova aggiuntiva del solo download privato: header dopo 11.334 ms, pacchetto completo dopo 22.805 ms, digest esatto. Entro timeout nativo privato 60 secondi per header/lettura e 180 secondi totali. È un campione, non p95/SLA.
- [Backup/restore definitivo](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38237793462): 465 documenti, 5 identità/claims, 12 allegati, 1 release privata disponibile verificata. Archivio SHA256 `5c4d6bc246fafa0e9f4e0896efcfacca920200d757bc65f4fa68b665a29afeea`, testedAt **12 ottobre 2026, 10:46:48 UTC**. Archivio offsite scaricato e autenticato anche localmente con la chiave privata esistente; nessun oggetto Storage da verificare. Password/provider/MFA produzione restano un gate distinto.
- Il telefono è stato scollegato prima dell'installazione di produzione: il tentativo si è fermato con device not found, senza sostituzione/disinstallazione. La prova fisica QA riuscita rimane valida; il pacchetto firmato definitivo è pronto per la sostituzione quando il dispositivo sarà disponibile.
- Utility ADC Windows: directory privata con ACL del proprietario prima della scrittura; syntax check e letture reali Firestore/Auth riusciti. Token e password non vengono stampati.

