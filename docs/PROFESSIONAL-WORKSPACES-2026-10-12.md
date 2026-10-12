# Revisione delle sezioni operative

Richiesta del 12 ottobre 2026: rendere le sezioni oltre la Panoramica più professionali, eleganti e allineate.

## Perimetro

Le 18 sezioni operative adottano superfici opache, bordi discreti, intestazioni e azioni coerenti, spaziature regolari e controlli uniformi. La successiva richiesta estende a tutte le pagine, inclusi Panoramica, accesso e portale clienti, uno sfondo ardesia con sfumature blu/lavanda e movimento lento. Il layout della Panoramica, compresi calendario, componenti e navigazione, rimane invariato.

Il foglio `professional-workspaces.css` limita le regole alle viste diverse da `view-dashboard`; i modali adottano il nuovo stile solo nelle sezioni operative. `ambient-canvas.css` gestisce lo sfondo condiviso, animato per 36 secondi e statico con movimento ridotto o stampa. Nessuna modifica ad API, dati o permessi.

## Modifiche

- Filtri infrastruttura, operatori e inventario con etichette visibili e area dedicata. Titoli, filtri e prima colonna delle tabelle condividono l'allineamento.
- Elenco antenne con sei colonne, parametri radio e diagnostici raggruppati, unità fisiche esplicite e azioni in una griglia. Tabelle con separatori discreti, numeri allineati e coordinate su due righe senza spezzare le cifre. Scorrimento confinato alla tabella sui display stretti.
- Centro report: intestazione compatta con azioni sulla destra, dettaglio/documenti a larghezza completa, filtri separati e stato compresso coerente. Sotto 600 pixel tutti i filtri occupano la larghezza completa del pannello. I report si aprono compressi e si espandono dalla freccia. Un refresh conserva quelli espansi; il ritorno alla sezione li comprime nuovamente. Filtri e azioni autorizzate rimangono funzionanti.
- Operatori con indicatori sugli account caricati, identità/profilo/presenza raggruppati, scheda dettagli, azioni secondarie espandibili, filtri azzerabili e form di collegamento con etichette visibili.
- Inventario: Modifica e Archivia affiancati, Movimenta nella riga successiva. Stato sistema: Servizi e Sessione hanno uguali dimensioni su desktop e mobile.
- Schede, form, profilo, monitoraggio, notifiche, strumenti e aree Enterprise con trattamento comune. Pulsanti e input uniformi, focus visibile e dimensioni touch adeguate.

## Verifica

Il test di conservazione carica la Panoramica con il nuovo sfondo condiviso ma senza il foglio delle sezioni operative, acquisisce screenshot e stili, quindi carica il foglio operativo tramite URL consentito dal CSP. Questo verifica che la revisione operativa non alteri il layout della Panoramica; lo sfondo comune cambia intenzionalmente come richiesto. Confronta gli screenshot tramite SHA256; se i byte differiscono, verifica tutti i pixel ammettendo al massimo 1/255 per componente colore, per il rumore di rasterizzazione dei bordi arrotondati. Dimensioni e stili devono coincidere esattamente. Controlla gli stili della Panoramica, navigazione, topbar, main e footer, comprese opacità e trasformazioni. Orologio e dati sono fixture deterministiche; si attende il completamento dei caricamenti prima del confronto. Le catture usano movimento ridotto e un viewport alto che contiene la vista completa; il messaggio temporaneo di sincronizzazione, esterno alla Panoramica, è nascosto in entrambe.

I nuovi test visitano tutte le 18 sezioni a 390, 768 e 1440 pixel, verificando contenimento di pannelli e controlli. Altri test controllano allineamento dei filtri/tabelle, dei pannelli notifiche e del pulsante invio, report compressi e dettaglio a larghezza completa. I test esistenti coprono ruoli, filtri, conferme, PDF, azioni e mantenimento del focus.

Gli screenshot usano esclusivamente dati sintetici:

- [Infrastruttura desktop](screenshots/professional-workspaces/infrastructure-desktop.png)
- [Centro report desktop](screenshots/professional-workspaces/reports-desktop.png)
- [Comunicazioni desktop](screenshots/professional-workspaces/communications-desktop.png)
- [Infrastruttura mobile](screenshots/professional-workspaces/infrastructure-mobile.png)
- [Centro report mobile](screenshots/professional-workspaces/reports-mobile.png)

- [Operatori desktop](screenshots/professional-workspaces/operators-desktop.png)
- [Operatori mobile](screenshots/professional-workspaces/operators-mobile.png)
- [Stato sistema desktop](screenshots/professional-workspaces/system-desktop.png)
- [Stato sistema mobile](screenshots/professional-workspaces/system-mobile.png)
- [Panoramica con sfondo condiviso](screenshots/professional-workspaces/overview-desktop.png)
