# RADIOTECH — Fascicolo impianto e prove coordinate

Branch: `feat/antenna-dossier` in entrambi i repository. PR web/backend #47; PR mobile #32. Il rilascio richiede entrambi; nessuna pubblicazione in produzione è avviata da questo lavoro.

## Le dieci funzioni concordate

1. Fascicolo: pulsante nella tabella Antenne sul web, pulsante nella scheda mobile dopo la risoluzione QR. Identità, dati tecnici, riepilogo, interventi/report storici, documenti pubblicati, fotografie, componenti e problemi. Le misure registrate manualmente non sono presentate come telemetria automatica.
2. Accesso al sito: indirizzo, referente, orari, parcheggio, ritiro chiavi, percorso e precauzioni; modifica riservata ai responsabili.
3. Configurazioni: revisioni aggiuntive immutabili con motivazione, autore e data. Confronto di due revisioni per parametro. Non applica configurazioni da remoto agli apparati e non sovrascrive automaticamente le specifiche della scheda Antenna.
4. Misurazioni: data/ora effettiva, parametro, unità, valore, soglie opzionali e note; grafici separati per unità, evidenza di fuori soglia. Le soglie appartengono alla misura, così lo storico non cambia a posteriori.
5. Dipendenze: collegamenti diretti orientati e consultazione della componente di rete collegata, anche con cicli. Il grafo documenta la rete; non simula guasti o garantisce percorsi di ridondanza. I collegamenti rimossi restano nello storico con motivazione.
6. Fotografie: PNG/JPEG/WebP fino a 10 MB, frecce, riquadri e note in coordinate normalizzate. L'originale resta separato dalle annotazioni; visualizzazione e download dell'originale sul web, visualizzazione e salvataggio esplicito nella galleria sul mobile.
7. Correzioni: gli operatori propongono modifiche alle indicazioni di accesso o ai parametri della configurazione; i responsabili approvano/respingono con motivazione. Se il valore originario è cambiato, l'approvazione viene respinta e va rivalutata. Originale, proposta e decisione restano conservati.
8. Pacchetto sopralluogo offline: download esplicito dal fascicolo mobile, comprendente metadati, documenti, fotografie originali, annotazioni, indicazioni e rete documentata. Home mobile > Fascicoli offline. Cifratura FlutterSecureStorage, separazione per tenant/account, durata massima 24 ore e cancellazione al logout. Il pacchetto precedente resta disponibile se il nuovo download fallisce. Non conferma modifiche o prove condivise offline.
9. Condivisione: il responsabile seleziona le sezioni e una durata da 1 a 720 ore. Copia congelata, accesso per token casuale di 256 bit; il database conserva solo il digest del token. Il link completo è mostrato solo alla creazione. La pagina pubblica usa il frammento URL, lo rimuove dalla barra prima delle richieste, non carica contenuti di terze parti e non espone le API interne. Letture e allegati richiedono ogni volta un link valido. Revoca, scadenza, conteggio richieste e ultime 100 richieste sono consultabili dal web. Il conteggio non rappresenta persone uniche. La revoca impedisce richieste successive: non può ritirare copie già lette o scaricate. Se si seleziona Accesso, anche le indicazioni sulle chiavi vengono condivise.
10. Ponte radio: la centrale sceglie due impianti e due operatori attivi distinti. Ogni prova congela le regolazioni e raccoglie una misura per estremo con autore e orario server. Solo il tecnico assegnato può confermare il proprio risultato; le misure confermate non sono modificabili e le correzioni richiedono una nuova prova. I risultati concorrenti dei due estremi sono uniti atomicamente, senza sovrascriversi. Chiusura solo dopo entrambi i risultati di tutte le prove. Aggiornamento ogni 10 secondi nella sezione aperta; sul mobile il polling si ferma in background. Non è una comunicazione radio/video né una sincronizzazione fisica degli strumenti.

## Autorizzazioni e concorrenza

Tutte le API interne verificano ruolo, tenant e proprietà dell'impianto. I clienti non accedono al fascicolo interno. Le operazioni riservate richiedono i ruoli manager esistenti. Un operatore può aggiungere evidenze, misure, problemi e proposte; i responsabili gestiscono dati ufficiali del fascicolo, componenti, dipendenze, risoluzione dei problemi, approvazioni, condivisioni e creazione delle sessioni.

Scritture Firestore transazionali, confronto di versione e ricevute idempotenti con digest del payload; revisioni/audit separati. Il grafo è una proiezione aggiornata nella stessa transazione delle dipendenze. Le nuove collezioni non sono esposte dalle Firestore Rules esistenti.

Collezioni: `antennaDossiers` (con operations/audit), `antennaDossierGraph`, `antennaDossierShares` (con accesses), `radioPairSessions` (con operations). Le query nuove usano singoli campi: nessun nuovo indice composto è richiesto. L'archivio precedente non viene migrato né cancellato.

## Configurazione e limiti espliciti

- Usare il backend keyless già previsto dal progetto. Per i link pubblici impostare `RADIOTECH_PUBLIC_BASE_URL` all'origine HTTPS raggiungibile dall'esterno; localhost HTTP è consentito solo per lo sviluppo.
- Massimo 750 KB per stato strutturato e copia condivisa; gli allegati sono separati. Nessun dato viene eliminato automaticamente per rientrare nel limite.
- 500 misure e 100 elementi per ciascuna altra sezione del fascicolo. La cronologia mostra fino a 200 incarichi e 200 report, con avviso quando parziale; i documenti testuali pubblicati sono limitati a 100, oltre agli allegati del fascicolo.
- Grafo: fino a 500 nodi documentati per tenant, 5000 archi analizzati e 100 nodi nella componente consultata; avviso esplicito se supera il limite.
- 40 annotazioni per fotografia. Offline: 50 allegati distinti e massimo 28 MiB per pacchetto JSON prima della cifratura. Il download deve completarsi interamente prima di sostituire la copia precedente.
- 100 prove per sessione. Le letture delle sessioni e dei link restituiscono al massimo 101 elementi; lo storico accessi mostra le ultime 100 richieste. I documenti completi rimangono nel database.
- L'utente deve scegliere il fuso locale corretto sul dispositivo per inserire data e ora. Il backend conserva gli istanti UTC.

## Verifiche

Backend: `DossierRulesTest`, `DossierEmulatorTest` (incluso in `npm run test:firestore`). Web: `web-tests/dossier-module.spec.js`, oltre alle suite esistenti. Mobile: `dossier_repository_test.dart`, `antenna_dossier_screen_test.dart`, analisi Flutter e suite Android esistenti. Nessuna dipendenza mobile nuova.

## Collaudo Samsung dopo l'integrazione

1. Aprire l'antenna dopo il QR e confrontare dati e revisione con il web.
2. Registrare due configurazioni, confrontarle, aggiungere una misura fuori soglia e una con unità diversa.
3. Scattare una fotografia, annotarla e controllare le coordinate anche dopo zoom e rotazione. Scaricare/salvare l'originale e verificare che non contenga i segni.
4. Proporre una correzione da operatore e approvarla dal web; simulare due revisioni concorrenti e verificare il rifiuto della versione obsoleta.
5. Scaricare il pacchetto completo, attivare modalità aereo, aprirlo dalla home e verificare documenti e fotografie. Cambiare account e verificare l'isolamento.
6. Creare un link con la sola Identità, aprirlo senza login e verificare l'assenza di Accesso e altre sezioni. Ripetere con fotografia/documento selezionato; revocare e verificare il rifiuto delle richieste successive.
7. Usare due account su due dispositivi per una sessione A/B: verificare che nessuno possa scrivere per l'altro estremo, che una ripetizione della richiesta non duplichi il risultato e che la chiusura richieda entrambi.
8. Verificare mappa delle dipendenze, collegamenti ciclici e rimozione motivata; confermare che i colori e i componenti restino coerenti con il tema esistente.
