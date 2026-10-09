# Personalizzazione aziendale, meteo e layout

La dashboard presenta ora, in ordine: presentazione, ora/meteo/scorciatoie, statistiche, accessi rapidi e note, mappa e stato rete, calendario. Note e accessi rapidi hanno colonne uguali; mappa e stato rete si estendono alla stessa altezza. I controlli usano una scala condivisa di 40 px, padding e raggi uniformi. Su desktop la topbar integra ricerca, tema, notifiche, azioni e account; su schermi piccoli la ricerca diventa un controllo compatto.

## Meteo gratuito

Il backend usa MET Norway Locationforecast 2.0, gratuito anche per uso commerciale con attribuzione CC BY 4.0. La ricerca località usa Photon / OpenStreetMap, con cache di un'ora e ricerca ritardata. I servizi pubblici richiedono uso moderato e non offrono un SLA.

- La scelta può avvenire per località, antenna o posizione del browser. La pagina consente geolocalizzazione solo alla propria origine; il consenso resta gestito dal browser.
- Cache delle previsioni per almeno 15 minuti e fino all'Expires del provider, richieste condizionali If-Modified-Since e massimo quattro decimali nelle coordinate.
- Richieste simultanee uguali vengono accorpate; un 429 rispetta Retry-After e non genera tentativi continui. Gli ultimi dati disponibili vengono indicati esplicitamente e conservati per un periodo limitato.
- Sono mostrate temperatura prevista, umidità, vento in km/h e cinque giorni successivi. Gli estremi giornalieri sono aggregati dai campioni nel fuso del browser. Informazioni non fornite dal provider non vengono inventate.
- Le vecchie impostazioni `radiotech.weather.endpoint` e `radiotech.weather.api-key` non selezionano più il provider precedente.
- Endpoint opzionali: `RADIOTECH_WEATHER_MET_ENDPOINT`, `RADIOTECH_WEATHER_LOCATION_ENDPOINT`; identificazione opzionale `RADIOTECH_WEATHER_USER_AGENT`. Il valore predefinito identifica il progetto con il suo repository pubblico. In un'installazione aziendale, impostare il dominio o contatto dell'azienda.

## Identità e preferenze

`Personalizzazione` permette agli amministratori e al Capo di cambiare nome, sottotitolo, copyright, email di supporto, logo, presentazione della dashboard, palette separate scuro/chiaro e testi dell'interfaccia. Include ricerca delle etichette, anteprima, annullamento, ripristino ed importazione/esportazione JSON.

Le impostazioni sono salvate dal backend in `tenantBranding/{tenantId}`, usando il tenant autenticato e mai un tenant fornito dal browser. La scrittura è limitata ad ADMIN, SUPER_ADMIN e CHIEF_EXECUTIVE. Non vengono accettati CSS, HTML eseguibile, SVG o URL esterni per il logo. Le etichette vengono inserite come testo e non cambiano enum, contratti API o dati operativi. La personalizzazione viene caricata dopo l'autenticazione: la schermata pubblica mantiene il brand di deployment, configurabile dalle variabili `RADIOTECH_BRAND_*` già esistenti.

Il menu Tema offre scuro/chiaro/sistema, contrasto elevato, testo ingrandito, carattere più leggibile e riduzione delle animazioni. Le preferenze sono locali al browser e non sovrascrivono quelle degli altri utenti. Il contrasto elevato prende precedenza sui colori aziendali. L'avviso nel configuratore misura il contrasto testo/pannello; non costituisce una certificazione WCAG dell'intero prodotto.

## Foto del Capo

La foto viene decodificata e ridimensionata nel browser; il ritaglio supporta trascinamento, zoom, controlli orizzontali/verticali e anteprima circolare. Il centraggio automatico del volto viene usato quando il browser offre FaceDetector; altrimenti si parte da un ritaglio centrale modificabile. Il risultato WebP circolare è salvato nel `photoUrl` del profilo esistente e visualizzato in profilo, sidebar e topbar. Il backend limita il payload a 350.000 caratteri; non serve un servizio di elaborazione immagini a pagamento.

## Statistiche

Il conteggio dei report in attesa nella dashboard ora usa un'unica query di uguaglianza sul tenant e filtra stato/rimozione sui documenti ottenuti. Evita di richiedere l'indice composito introdotto dalla combinazione tenant/status/removedAt. Questo rimuove una possibile causa del 500, ma non certifica la causa del problema in produzione senza i log della richiesta. Il conteggio comporta letture della raccolta report del tenant; per volumi elevati, un contatore transazionale dedicato è preferibile.

## Verifica

- Build Java 21 / bootJar riuscita. Suite backend: 242 test, 159 passati e 83 saltati perché richiedono emulatori Firebase; nessun fallimento. Nell'ambiente di verifica Mockito è stato avviato come javaagent esplicito, senza cambiare la configurazione del progetto.
- 31 test browser sull’app Spring Boot compilata: geometria dashboard, personalizzazione persistente, autorizzazioni viewer, ritaglio circolare, tema chiaro/scuro, ingrandimento del testo, gestione errori meteo e responsive su nove risoluzioni (320–1920 px).
- Risposta reale del provider verificata per Lecce; i test automatici usano risposte controllate per non dipendere dalla rete.
- La verifica di produzione delle statistiche richiede i log della richiesta 500 e un accesso autenticato: non sono stati usati account o credenziali di produzione.
