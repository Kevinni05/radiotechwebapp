# Correzione report e incarichi su Render — 8 ottobre 2026

## Risultato e causa verificata

L’API pubblica è `https://radiotechwebapp.onrender.com`. Il bucket configurato
`gestionale-radio.firebasestorage.app` non esiste: la correzione precedente che
usava Firebase Storage non poteva recuperare gli allegati locali sul server.

La diagnosi in sola lettura ha rilevato 8 incarichi, 3 report e 4 assegnazioni
con il campo storico `operator_uid`. I 9 allegati originali (1.561.029 byte)
erano presenti nell’archivio locale. Sono stati copiati in Firestore e riletti,
verificando SHA-256, autore e azienda per ogni file. Gli identificativi originali
sono conservati: nessun report, incarico o operatore è stato azzerato.

## Comportamento aggiornato

- In produzione gli allegati usano Firestore (`reportFiles/{id}/chunks/{index}`),
  con blocchi da 256 KiB, limite per file di 10.000.000 byte e scrittura atomica.
  Accesso e integrità passano dagli endpoint autenticati `/api/v1/files`.
  Le regole Firestore impediscono l’accesso diretto ai file tramite SDK.
- `RADIOTECH_ATTACHMENT_BACKEND=FIRESTORE` è il valore predefinito in produzione.
  Non occorre creare un bucket. `FIREBASE_STORAGE` resta selezionabile dopo aver
  effettivamente configurato quel servizio; il filesystem locale è vietato in produzione.
- Report e incarichi riconoscono ID profilo, UID Firebase e campi storici.
  Gli elenchi includono documenti senza data di ordinamento; i timestamp storici
  vengono convertiti per le risposte API. Le assegnazioni mostrano il profilo e il nome corretti.
- Gli incarichi web sono tutti consultabili, senza il precedente taglio a 50 righe.
  I responsabili possono annullare quelli ancora operativi, conservandoli nello storico.
- Gli incarichi con report già inviato non impediscono più la rimozione dell’operatore.
  Per un incarico ancora operativo il messaggio indica titolo e ID da annullare.
  Restano i controlli sul proprio account, sui ruoli privilegiati e sull’azienda.
- Un report rifiutato riporta atomicamente il suo incarico in `COMPLETED`,
  rendendo nuovamente disponibile la compilazione sul mobile.
- Lo storico mobile si aggiorna al rientro nell’app e dopo una submission;
  resta consultabile anche se fallisce la richiesta accessoria degli incarichi.
  Un PDF originale indisponibile può essere ricostruito dai dati registrati,
  con una nota esplicita che fotografie e firma originali non sono state recuperate.
- Il contatore report mobile usa la stessa API dello storico. Gli upload passano
  sempre dal backend e i tempi di attesa sono adeguati all’avvio di Render.
- Il backup ricorsivo include i file e recupera i blocchi singolarmente.

## Verifiche

- Suite Java e build JAR.
- 63 test di integrazione Firestore/Auth, inclusi nuovi casi su dati storici,
  revisione/rimozione, file da 10 MB, integrità e recupero degli allegati.
- 10 test delle regole, incluso il divieto di accesso diretto ai nuovi file.
- 40 test browser, inclusi incarichi oltre la cinquantesima riga e annullamento.
- Mobile: analisi senza segnalazioni, 109 test e APK Android di test compilato.
- APK 1.4.2, build 2020, configurato per Render e installato come aggiornamento
  sul telefono collegato. Il codice e la CI mobile si trovano nel repository
  `Kevinni05/gestionale_radio`; il push del backend aggiorna invece Render.

## Miglioramenti consigliati dopo questa correzione

1. Monitorare spazio e operazioni Firestore, e prevedere uno storage per oggetti
   quando il volume di foto/report cresce. Questa soluzione rispetta i limiti
   dei documenti e delle richieste, ma ogni blocco comporta letture e scritture.
   [Limiti ufficiali Firestore](https://firebase.google.com/docs/firestore/quotas).
2. Aggiungere paginazione e query mirate agli archivi. La compatibilità attuale
   filtra i dati dell’azienda sul backend; archivi grandi aumentano il costo delle letture.
3. Verificare backup automatici esterni, prove periodiche di ripristino e allarmi
   su quota esaurita, report pendenti e disponibilità dell’API.
4. Collegare l’etichetta ONLINE della home mobile alla connettività effettiva;
   attualmente è un indicatore grafico statico.
5. Predisporre una distribuzione Android firmata di produzione e un canale di
   aggiornamento. L’APK consegnato qui è una build di test; iOS richiede una verifica separata.

Gli spostamenti contemporanei dei documenti nella cartella `Files MD` sono
esclusi dal commit della correzione. Il Dockerfile resta nella radice richiesta da Render.
