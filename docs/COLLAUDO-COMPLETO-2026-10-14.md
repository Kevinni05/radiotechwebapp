# Collaudo integrato RADIO TECH — 14 ottobre 2026

Stato: flusso operativo candidato verificato su Samsung e dashboard; accesso nell’app normale al servizio pubblico riuscito, confermato da Kevin. Questo verbale integra il [registro di accettazione](DELIVERY-ACCEPTANCE-2026-10-13.md). La conclusione tecnica non equivale al Go commerciale del piano.

## Sorgenti e controlli

Web/API collaudati: `7b6088b9a054f359f3b3388edbe0b9c3ea44e264`; [CI completa riuscita](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38554088372): autorità, backend, regole e integrazioni, Chromium, Firefox, WebKit, container e artefatto riproducibile. Mobile: codice prodotto `f304070945c18a1655278738bf01dbe498e6f370`, sonda privata `2a37cd3e4f7d7a0f498e24868d4b79eb594dfc71`; [CI mobile riuscita](https://github.com/Kevinni05/gestionale_radio/actions/runs/38554095848), distribuzione Firebase saltata come previsto.

Controlli locali: analisi Flutter pulita, 184 test Flutter, 113 test di integrazione backend in 39 suite senza casi saltati, 17 prove regole, 16 contratti servizi web, 10 prove autenticazione/sessione. Nove regressioni del risultato di revisione report passate sui tre browser. Questi conteggi descrivono suite diverse e non vanno sommati come operazioni fisiche.

## Flusso dashboard → Samsung → dashboard

Il responsabile ha effettuato un accesso reale nella dashboard, con le verifiche dell'autorità. Il tecnico QA usa Firebase Auth reale con ruolo OPERATOR; nessun claim amministrativo fittizio. Backend candidato su loopback con identità indipendente e database operativo emulato. Nessun incarico o report di cliente modificato.

| Passaggio | Esito osservato |
|---|---|
| Assegnazione dalla dashboard | Incarico QA disponibile sul Samsung |
| GPS reale e conferma | Kevin conferma check-in riuscito; backend registra GPS e IN_PROGRESS |
| Checklist e bozza | Quattro voci, note, foto reale di prova, quantità 1 e sigla visibili |
| Termine processo e riapertura | Kevin conferma conservazione di tutti i dati della bozza |
| Risposta persa dopo commit | Proxy chiude la risposta dopo il 201; stesso report e chiave mantenuti |
| Retry con risposta trattenuta | Due retry 503 senza ulteriore commit; rilascio e retry 201, invio confermato |
| Foto, firma e PDF | Tre allegati recuperabili, lunghezze/digest/ETag verificati; PDF nativo di tre pagine con note e materiale QA |
| Due approvazioni concorrenti | Stesso responsabile, risposte reali 400 e 200; report APPROVED, un solo consumo |
| Rimozione dalla dashboard | Report escluso dall'elenco, removedAt registrato, audit conservato, stock invariato |
| QR fisico | Kevin conferma apertura dell'antenna QA corretta dalla scansione |

La scansione QR è stata eseguita dopo il rapporto, sulla stessa antenna: non si dichiara un ordine fisico QR→GPS mai eseguito. Stock iniziale 3, finale 2; un movimento inventario. Audit REPORT_SUBMITTED, REPORT_APPROVED e REPORT_REMOVED. La firma manoscritta è un allegato PNG inserito nel PDF; il campo separato digitalSignature non è valorizzato.

Per questa prova gli allegati del backend QA usano LOCAL_FILESYSTEM privato. Il ramo Firestore e le regole Storage sono coperti dalle rispettive suite, ma questo invio fisico non prova il percorso allegati di produzione. Il primo errore 500 era dovuto al segreto di verifica report omesso nell'avvio QA: configurazione corretta conservando la coda; la simulazione dopo commit è stata eseguita successivamente e documentata separatamente.

## Isolamento e accesso

Nove verifiche HTTP con un autentico account sintetico di un altro tenant: autenticazione valida, antenna/history non leggibili, QR e accettazione incarico estranei respinti, statistiche gestionali vietate, stock estraneo assente, richiesta anonima negata, token precedente di account disabilitato respinto con 401. Gli endpoint legacy QR/accettazione rispondono 400 senza contenuti della risorsa: non si dichiara un codice 403 inesistente.

Le prove fisiche precedenti di push nei tre stati, due destinatari sequenziali, revoca dispositivo e SDK sono nel [verbale Samsung](SAMSUNG-QA-2026-10-14.md), sullo stesso codice prodotto mobile. Non sono nuove prove simultanee su più dispositivi.

## Difetti e correzioni

- Accesso dashboard QA: origine CORS errata nella configurazione locale corretta. Il client ora gestisce anche risposte CORS/HTML/non JSON con messaggi comprensibili, senza mostrare eccezioni del parser.
- Revisione report concorrente: una risposta di conflitto poteva mostrare fallimento benché l'altra approvazione fosse riuscita. Il client rilegge lo stato e distingue approvato, in corso ed errore reale; nove regressioni browser superate.
- Accesso app normale: Kevin rileva «the custom token corresponds to a different audience». L'autorità pubblica 00004 usava il firmatario del progetto registro. Su autorizzazione esplicita, traffico dell'autorità passato alla 00006 con firmatario dedicato in gestionale-radio; Kevin conferma «Entro nella home»: nuovo accesso pubblico riuscito. Il backend pubblico resta alla revisione precedente.

## Aggiornamento Android

APK normale firmato 1.5.5 (11005) installato con aggiornamento, senza disinstallazione. Stesso certificato SHA256 `73005990b6bad93b1bb3383121f6dcc2293b3e5e3a60e1ae7eaf09a7d97c4fa4`; appId 10426 e inode dati CE 220177 / DE 353071 conservati. Avvio a freddo riuscito, processo vivo, nessun errore fatale di avvio rilevato. La conservazione del contenuto di bozze/coda della versione commerciale non è stata ispezionata; la conservazione della bozza completa è provata nell'app QA separata.

[Manifest 11005](RELEASE-CANDIDATE-11005.json). APK, AAB, simboli, JAR, PDF e prove selezionate sono nel pacchetto privato `~/.radiotech/releases/1.5.5-11005-rc-20261014`. APK QA, password, token, chiavi, coordinate GPS e log grezzi esclusi dal pacchetto commerciale e da Git.

## Verifiche ancora aperte

Distribuzione governata del backend e regole candidate; revoca account/cambio ruolo nel percorso distribuito; ricezione simultanea su più dispositivi; TalkBack fisico declinato da Kevin; pilota con misure, custodi, supporto, backup offsite e decisione Go. Un solo Samsung disponibile. Il restore del registro separato è già verificato su 90 documenti e un segreto nel [verbale dedicato](IDENTITY-RECOVERY-2026-10-13.md), ma non sostituisce il restore operativo completo e le responsabilità di consegna.


## Chiusura delle risorse QA

Logout della sonda verificato con phase=cleaned; account Auth sintetici e due presenze RTDB eliminati. Tre file/allegati operativi della fixture e relativi metadati rimossi dopo verifica UID, tenant e digest; PDF e prove conservati privatamente. Rimosso il file di coordinate QA dal telefono. Server backend, emulatore Firestore e proxy temporanei arrestati, browser QA chiuso, reverse USB rimossi, credenziale impersonata e segreto temporaneo cancellati. Il profilo browser privato precedente resta conservato: la sua eliminazione ricorsiva era stata respinta dalla revisione automatica in una sessione precedente e non è stata ritentata.

Tag qa-device rimosso. Diversamente dalla sessione precedente, il traffico ordinario resta al 100% sulla revisione 00006, come autorizzato da Kevin per correggere l'accesso; nessun rollback automatico alla revisione difettosa. L'app normale 11005 resta installata e aperta. Le risorse e i dati degli altri utenti sono conservati.

Verifica di produzione: Kevin conferma accesso alla home dell’app normale dopo la correzione dell’autorità. Kevin conferma inoltre caricamento senza errori di Incarichi ed elenco antenne. Nessuna operazione aziendale reale richiesta.
