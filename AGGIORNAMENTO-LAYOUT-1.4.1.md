# RadioTech 1.4.1 — layout e report

## Mobile
Il calendario è stato rimosso dalla schermata Mappa antenne. Rimane accessibile tramite il pulsante Calendario nella home.

Note del calendario: spazio interno ai box, campo multilinea, pulsanti distanziati e contenuti che si adattano al testo ingrandito. Enterprise Pro: larghezza di lettura centrata, separazione fra selezione e ricerca, schede e comandi con spazi coerenti; stessa larghezza applicata agli editor.

Assistente AI: conversazione e composizione nella stessa pagina scorrevole, senza un riquadro chat con altezza fissa. Indicatori su righe adattabili e con la stessa altezza per riga; avvisi visibili prima degli indicatori. I testi lunghi si espandono senza sovrapporsi.

Compilazione report: palette corallo condivisa, testi secondari e suggerimenti con contrasto migliorato, parametro e valore su due righe nei telefoni stretti, spazi coerenti. Resta attiva la SafeArea per la barra Android.

Storico: un riepilogo per compito, ordinato per data, ricerca per titolo/impianto/note, titolo del compito e stato leggibili. I singoli allegati non riempiono più le schede. Apri PDF riepilogativo permette la visualizzazione e stampa; Scarica PDF apre il selettore Android per scegliere dove salvare il solo documento PDF. Scegli Downloads o una cartella del dispositivo e conferma Salva. Annullare lascia il documento non salvato senza segnalare un successo.

Il PDF originale viene preservato quando esiste. Per gli interventi vecchi senza PDF originale, il riepilogo viene ricostruito dai dati disponibili e contiene una dicitura che lo specifica: immagini, firme e dati mancanti non vengono inventati. Un errore nel recupero del documento consente di riprovare.

## Web
Contenuti e comandi restano entro i box: larghezze minime corrette, testo lungo a capo, gruppi di pulsanti adattabili, margini uniformi. Centro report: titolo nel formato Report-Titolo compito assegnato-Nome Operatore-Data. I nuovi report memorizzano il titolo del compito e il nome dell’antenna; i vecchi usano i metadati disponibili senza mostrare un codice come nome.

Il layout a tutta larghezza dei report rispetta filtri e compressione. È stato corretto anche il recupero tardivo delle note del calendario: una risposta arrivata dopo l’inizio della modifica non cancella la bozza.

## Verifiche
104 test Flutter superati; ulteriore ricontrollo finale di report e nuovo layout superato (9 test). Analisi senza segnalazioni. Test backend ed emulatori Firestore/Auth superati. 35 scenari web verificati fra esecuzione generale e ricontrolli; prova autenticata HTTPS completa superata (login QR, incarichi, report e allegati). Esiti nei log .dist/layout-refinement-*.

APK 1.4.1, versionCode 2019, ARM64: firma e allineamento ZIP 16 KB verificati; installato sul Samsung S25 FE senza disinstallazione. SHA256: 2A21F1558DBC0539B5AE7C20B42DF3343DFF5FAAA1EE7344323C8EF2CB005DC0.

## Verifica Blaze
Il progetto gestionale-radio risulta con billingEnabled=true. Il database (default) è Firestore Native Standard e non mostra il vincolo freeTierLimited. Le letture dirette sono riuscite anche con le credenziali del backend. Il backend è stato riavviato, azzerando la pausa temporanea delle automazioni impostata dopo i precedenti errori di quota. La prova HTTPS completa è superata; esito nel log .dist/layout-refinement-https.log.
