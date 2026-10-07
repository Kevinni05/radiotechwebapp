# RadioTech 1.4 — aggiornamento operativo

## Mobile
La barra Android viene rispettata con una SafeArea globale. La mappa è nelle Azioni operative; Riparazioni è stata rimossa. Il calendario completo resta accessibile tramite pulsante: le note giornaliere sono disponibili anche sotto la mappa.

Nuovo report consente di scegliere un’antenna, il tipo di intervento e una lista di controllo modificabile. Lo storico riunisce report e incarichi completati; i PDF allegati originali possono essere visualizzati, condivisi e stampati. I documenti preesistenti conservano i contenuti effettivamente ricevuti: immagini o firme mancanti non vengono ricostruite.

AI e previsioni utilizza pulsanti e indicatori riallineati. TelcoTools resta il riferimento per i calcolatori RF/Telecom.

## Web
La home include data/ora, meteo e calendario con note personali e promemoria. Il meteo usa inizialmente le coordinate di un’antenna; il pulsante posizione consente di scegliere la posizione del browser. Se il servizio meteo non risponde, il widget segnala l’indisponibilità.

Centro report: filtri per date, operatore, stato e testo; compressione di ogni report e di tutte le schede. L’audit operativo si comprime tramite freccia.

Magazzino: deposito, corsia, scaffale e posizione; carichi, scarichi, rettifiche, lotti e scadenze; scorte minime, storico movimenti ed esportazione CSV. Le quantità vengono aggiornate in transazione. I consumi dei report privilegiano i lotti validi con scadenza più vicina; i lotti scaduti non sono consumabili. L’aggiornamento in tempo reale utilizza Firestore quando autorizzato, con aggiornamento periodico di riserva. La dashboard non costituisce una certificazione di prestazioni su milioni di articoli: dimensionamento e paginazione vanno validati sui volumi del cliente.

TelcoTools web comprende calcolatori IP/IPv6/VLSM, VLAN, radio, fibra, manutenzione ed energia, lista di controllo e riepilogo esportabile. I calcoli LTE/NR sono parametrizzati: verificare i parametri della banda utilizzata.

## Badge e sicurezza
Nel badge dell’operatore e nel suo elenco sono disponibili Validità QR, Reimposta password e rigenerazione. La validità può essere standard (30 giorni), a data fissa o senza scadenza. Il QR resta utilizzabile per un solo accesso: la scadenza illimitata non lo rende riutilizzabile. Dopo una scadenza fissa occorre configurare una nuova scadenza valida.

Il QR personale mobile utilizza immagine e contenuto generati dal backend. Può essere salvato nella galleria. L’app verifica gli aggiornamenti tramite ascolto del profilo, controllo periodico, ripresa dell’app e messaggio di aggiornamento in background. Android può ritardare il lavoro in background; riaprendo l’app viene effettuata una nuova verifica. Un’immagine già salvata in galleria va scaricata nuovamente dopo la rigenerazione.

Reimposta password invia il collegamento alla mail registrata e rigenera il badge soltanto dopo l’accettazione della richiesta da parte del servizio di autenticazione. La consegna della mail dipende dal provider e dai filtri della casella.

La cancellazione definitiva rimuove l’operatore e l’account di autenticazione, preservando i report storici. Vengono bloccati l’autoeliminazione, gli account privilegiati e gli operatori con incarichi ancora attivi.

## Ambiente di prova e futuro deploy
Il collegamento HTTPS attuale continua a utilizzare Cloudflare Tunnel: PC, backend e tunnel devono restare accesi. Il collegamento gratuito temporaneo può cambiare a un riavvio; in tal caso aggiornare la configurazione pubblica e rigenerare i QR.

Il meteo Open-Meteo gratuito è destinato a valutazione e uso non commerciale. Per la vendita/deploy commerciale configurare un endpoint autorizzato e l’eventuale chiave tramite radiotech.weather.endpoint e radiotech.weather.api-key. Condizioni ufficiali: https://open-meteo.com/en/pricing.

La build Android installata è una build di test con App Check debug. Per distribuzione aziendale occorrono firma release, App Check di produzione, dominio stabile e le verifiche di carico e sicurezza dell’ambiente scelto.

## Verifica finale e impedimento del database cloud

98 test mobile superati, analisi senza segnalazioni; tutti i 33 scenari web superati (32 nella suite completa e assegnazione incarichi nel ricontrollo dopo aggiornamento del testo atteso). Unitari backend ed emulatori Firestore/Auth superati. Installazione 1.4.0/2018 verificata sul telefono; firma e allineamento APK corretti.

Il 7 ottobre alle 02:03 circa il progetto Firestore reale ha risposto `RESOURCE_EXHAUSTED: Quota exceeded`. I primi tentativi HTTPS si sono fermati su questa indisponibilità. Un successivo controllo è riuscito a completare login e associazione del dispositivo, verificando che QR web/mobile abbiano contenuto e PNG identici, e che calendario e storico siano accessibili. La verifica dei restanti passaggi viene riportata nel log dello smoke test.

È stata aggiunta la risposta HTTP 503 in italiano per distinguere la quota esaurita da password errate o sessioni revocate. Le automazioni sospendono i tentativi per 30 minuti quando rilevano la quota esaurita. Nessun piano a pagamento è stato attivato.

Se il limite esaurito è la quota gratuita giornaliera, viene ripristinato attorno alla mezzanotte del Pacifico (nel periodo di questo test, circa le 09:00 italiane); il messaggio del provider non specifica quale limite sia stato raggiunto. Occorre verificare la voce Utilizzo e le quote nella console Firebase. Fonte: https://firebase.google.com/docs/firestore/quotas.

### Esito dello smoke test HTTPS finale

La ripetizione finale è terminata con successo: login QR e sessione dispositivo, QR personale identico web/mobile, calendario/storico autorizzati, incarico visibile e avanzamento operativo, caricamento e recupero allegato PDF, invio report e ripetizione senza duplicati, visibilità del report lato web, 19 sezioni Pro, generazione e decodifica QR antenna, allegati locali e stato del backend. I dati temporanei del flusso QR/report sono stati rimossi dalla procedura di prova.

L’errore di quota osservato rimane un limite dell’ambiente cloud: il successivo successo non garantisce che non ricompaia. La gestione 503 e la pausa delle automazioni restano attive.
