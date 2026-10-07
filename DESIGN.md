# RadioTech Signal

Direzione visiva per la web app e il portale clienti. Riferimento scelto dopo il confronto tra Raycast, Factory, Brex e Superhuman su Refero: [Raycast](https://styles.refero.design/style/3b6a17f0-3bdf-418c-a95e-0b89e5a8b2f8). È un adattamento originale per una console operativa, non una copia dei componenti o degli asset del sito di riferimento.

## Linguaggio visivo

Una console scura, precisa e leggibile. Superfici antracite, comandi chiari, accento corallo discreto e grafica astratta del segnale. Le informazioni operative hanno precedenza sulle decorazioni. La grafica SVG è originale; il logo rimane quello ufficiale di RadioTech.

- Sfondo `#08090b`; pannelli `#121316`; superfici secondarie `#191a1e`.
- Testo principale `#f4f4f5`; secondario `#ababb5`; bordi `#2b2c32`.
- Corallo `#ff6363` per indicatore di navigazione, focus e grafica. Pulsanti principali chiari con testo scuro.
- Stati funzionali distinti: verde, ambra, rosso e blu, sempre accompagnati da testo.
- Inter variabile ospitato localmente, con licenza SIL OFL conservata accanto al font. Corpo 14 px, etichette 12 px, intestazioni adattive da 27 a 46 px; cifre tabulari per metriche.
- Spaziatura basata su 4/8 px; pannelli 14 px, pulsanti 8 px; padding normalmente 24 px.

## Layout e interazioni

Sidebar organizzata in Operazioni, Risorse, Intelligence e gestione, con Account e sistema separati. Barra superiore persistente, contenuto fluido e griglie con colonne `minmax(0,1fr)`. I campi hanno etichette visibili; note e footer del modulo occupano tutta la larghezza. Schede, modali, tabelle e stati vuoti condividono lo stesso tema.

Il cursore rimane quello di sistema. Animazioni leggere per lo sfondo e le transizioni; feedback breve dei pulsanti. `prefers-reduced-motion` disattiva le animazioni decorative. La grafica del segnale è decorativa, non contiene dati simulati e non intercetta i click. Sugli schermi piccoli la navigazione diventa un pannello richiudibile e i moduli usano una sola colonna.

La variante glass usa superfici traslucide con bordi luminosi sottili e blur di 18 px, ridotto a 10 px sui box mobili. Lo sfondo combina gradienti viola, corallo e blu con una trama geometrica e un movimento di 28 secondi. In assenza di supporto per il blur, i pannelli conservano una superficie opaca leggibile. I box della stessa griglia hanno larghezze e altezze uguali; le coppie di pannelli usano colonne simmetriche e i contenuti lunghi determinano l'altezza necessaria, senza tagli o dimensioni fisse condivise fra pagine diverse.

Il logo della console torna alla dashboard nella sessione corrente, chiude il menu mobile e torna all'inizio della pagina; quello del portale torna alla home del cliente senza uscire dall'account. Il copyright «© 2026 Kevin Cagnazzo e Anthony Piccinonno. Tutti i diritti riservati.» compare nel login e nei footer della console e del portale.

## Implementazione

`design-system.css` conserva le regole strutturali responsive; `radiotech-theme.css` definisce il tema comune. `customer-portal.css` contiene soltanto la struttura del portale. Le icone sono SVG locali e il tema non richiede font, immagini o script di terze parti durante l'utilizzo.

Verificare sempre tutte le sezioni da 320 a 1920 px, l'accessibilità da tastiera, i modali, testi lunghi, le preferenze di animazione e i flussi di gestione dopo una modifica strutturale.

## Verifica del rinnovo

Per la variante glass sono stati verificati 27 percorsi: 26 test applicativi/responsive superati nella suite completa e il nuovo test di dimensioni, blur, logo-home, sessione preservata, tastiera, menu mobile e preferenza di movimento superato separatamente dopo la correzione del selettore del test (misura le schede reali della dashboard). Log `.dist/glass-web-tests.log` e `.dist/glass-home-tests.log`. Risorse e copyright confermati sul server locale 8080; pacchetto `bootJar` compilato correttamente.

Il 6 ottobre 2026 la suite Chromium ha superato 26 test in 2,5 minuti: tutte le sezioni a sette larghezze da 320 a 1920 px, moduli Enterprise Pro e accessi, gestione operativa, tastiera e focus, animazioni ridotte e nuovo portale clienti con login, creazione richiesta e logout. Le API dei flussi browser sono simulate; template, font, SVG, risorse, protezione HTTP non autenticata e CSP sono serviti dal backend reale di test. Il server locale 8080 è stato verificato con tema disponibile, dashboard aggiornata e health UP. Log: `.dist/signal-web-tests.log`; schermate: `build/reports/web/redesign-login-desktop.png`, `build/reports/web/redesign-overview-desktop.png`, `.dist/signal-portal-login-desktop.png` e relative varianti mobili.
