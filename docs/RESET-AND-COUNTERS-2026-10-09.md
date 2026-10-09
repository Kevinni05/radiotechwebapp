# Reset operativo e contatori — 9 ottobre 2026

Su richiesta del proprietario sono stati azzerati i dati operativi dell'azienda presente su `https://radiotechwebapp.onrender.com`.

Sono rimasti invariati **9 operatori e 2 antenne**, comprese le loro proprietà e le eventuali sottocollezioni. Sono conservati i **14 account Firebase Authentication**, il profilo di accesso, i dispositivi autorizzati e i metadati del backup.

Il reset ha eliminato 8 incarichi, 3 report, 9 allegati con 13 blocchi Firestore, 2 interventi, 5 articoli di magazzino, 1 movimento, 1 nota calendario, 11 notifiche, 5 ricevute, le chiavi delle vecchie operazioni, 69 eventi storici e il documento di prova. Non sono state modificate le credenziali di accesso.

Prima del reset è stato verificato un backup cifrato e autenticato con **159 documenti, 14 account e 40 file locali**. Copia locale: `.dist/backups/pre-reset-20261009-011942768.rtbackup`; SHA-256: `20af111679e74e66248e6a82dea1b279e4cc93194d835f6dce0c0549beb175ed`. L'archivio e la chiave restano esclusi da Git. Anche il backup esterno con prova di ripristino è riuscito: [esecuzione GitHub Actions](https://github.com/Kevinni05/radiotechwebapp/actions/runs/37868291287).

## Correzioni

- La lista operatori include i profili senza `createdAt`, legge i nomi e le date nel formato precedente e ordina i risultati senza escludere anagrafiche. Lo stato storico `ACTIVE` viene visualizzato come `ATTIVO`, senza riscrivere i documenti.
- I contatori della home usano i totali aziendali del backend; incarichi attivi e scaduti escludono le attività completate o già inviate in revisione.
- L'Agenda conta i report effettivi in `SUBMITTED` o `APPROVAL_PENDING` e consente di aprire direttamente il Centro report filtrato. Il filtro viene applicato sul server prima della paginazione, così include anche report oltre la prima pagina.
- Per `APPROVAL_PENDING` è disponibile **Riprendi approvazione**. Restano le protezioni contro approvazioni simultanee e consumi duplicati di magazzino; non viene consentito un rifiuto dopo l'inizio della contabilizzazione.
- Il meteo è stato verificato sull'API e nel browser su Render. Le coordinate salvate nel vecchio formato `lat`/`lng` vengono normalizzate prima della richiesta; il pulsante di aggiornamento permette di riprovare dopo un errore del servizio. [Documentazione del fornitore](https://open-meteo.com/en/docs).
- È stato pubblicato l'indice Firestore per l'archivio report filtrato per stato e ordinato per data.

## Procedura di manutenzione

`operationalReset` è un comando locale per l'amministratore, non un endpoint dell'app. Richiede progetto, credenziali, azienda esplicita e backup autenticato. La modalità predefinita è `dry-run`. La modalità `apply` verifica che i dati selezionati siano presenti nel backup, elimina in un unico batch con precondizioni di versione e controlla che operatori e antenne siano invariati. Conserva le altre aziende e rifiuta collezioni non classificate. Oltre 450 documenti richiede una procedura di manutenzione dedicata.

```powershell
.\gradlew.bat operationalReset -PresetMode=dry-run -PbackupArchive=<archivio> -PbackupKey=<file-chiave> -PresetTenant=<azienda>
```

La verifica su emulatori comprende conservazione delle anagrafiche e dei dispositivi, cancellazione dei blocchi allegati, isolamento aziendale e rifiuto di backup vecchi o documenti modificati durante il reset. I test del browser coprono contatori globali, accesso ai report oltre la prima pagina e meteo con coordinate storiche e recupero dopo un errore.
