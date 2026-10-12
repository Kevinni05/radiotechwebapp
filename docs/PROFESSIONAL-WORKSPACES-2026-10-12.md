# Revisione delle sezioni operative

Richiesta del 12 ottobre 2026: rendere le sezioni oltre la Panoramica più professionali, eleganti e allineate.

## Perimetro

Le 18 sezioni operative adottano superfici opache, bordi discreti, intestazioni e azioni coerenti, spaziature regolari e controlli uniformi. Il canvas e il footer diventano sobri quando è attiva una sezione operativa. La Panoramica, compresi calendario, componenti, navigazione e footer nella vista iniziale, mantiene gli stili precedenti.

Il foglio `professional-workspaces.css` limita le regole alle viste diverse da `view-dashboard`. Il cambio di canvas/footer è condizionato alla vista attiva; i modali adottano il nuovo stile solo nelle sezioni operative. Nessuna modifica a API, dati o permessi.

## Modifiche

- Filtri infrastruttura, operatori e inventario con etichette visibili e area dedicata. Titoli, filtri e prima colonna delle tabelle condividono l'allineamento.
- Tabelle con separatori discreti, numeri allineati, azioni organizzate e coordinate su due righe senza spezzare le cifre. Scorrimento confinato alla tabella sui display stretti.
- Centro report: intestazione compatta con azioni sulla destra, dettaglio/documenti a larghezza completa, filtri separati e stato compresso coerente. Filtri e azioni autorizzate rimangono funzionanti.
- Schede, form, profilo, monitoraggio, notifiche, strumenti e aree Enterprise con trattamento comune. Pulsanti e input uniformi, focus visibile e dimensioni touch adeguate.

## Verifica

Il test di conservazione carica la Panoramica senza il nuovo foglio, acquisisce screenshot e stili, quindi carica il foglio tramite URL consentito dal CSP. Confronta gli screenshot byte-per-byte tramite SHA256 e gli stili della Panoramica, navigazione, topbar, main e footer. Orologio e dati sono fixture deterministiche; si attende il completamento dei caricamenti prima del confronto.

I nuovi test visitano tutte le 18 sezioni a 390, 768 e 1440 pixel, verificando contenimento di pannelli e controlli. Altri test controllano allineamento dei filtri/tabelle, dei pannelli notifiche e del pulsante invio, report compressi e dettaglio a larghezza completa. I test esistenti coprono ruoli, filtri, conferme, PDF, azioni e mantenimento del focus.

Gli screenshot usano esclusivamente dati sintetici:

- [Infrastruttura desktop](screenshots/professional-workspaces/infrastructure-desktop.png)
- [Centro report desktop](screenshots/professional-workspaces/reports-desktop.png)
- [Comunicazioni desktop](screenshots/professional-workspaces/communications-desktop.png)
- [Infrastruttura mobile](screenshots/professional-workspaces/infrastructure-mobile.png)
- [Centro report mobile](screenshots/professional-workspaces/reports-mobile.png)
