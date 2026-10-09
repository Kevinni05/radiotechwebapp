# Meteo dopo il deploy

Segnalazione: GET `/api/v1/weather?latitude=40.35481&longitude=18.17244` risponde 500 su Render anche premendo aggiorna.

## Evidenza e limiti

La richiesta raggiunge il backend: lo status 500 non è un blocco CORS nel browser. Il controller precedente lasciava propagare gli errori del client esterno al gestore generico, che restituiva soltanto un messaggio di errore interno. Anche il widget nascondeva il dettaglio ricevuto.

La cache meteo è in memoria e viene persa a ogni riavvio/deploy. La prima richiesta torna quindi a dipendere dal provider. Questo spiega una vulnerabilità al riavvio, ma **non prova la causa del 500 specifico di Render**.

La chiamata Java di RadioTech alle coordinate segnalate ha funzionato in questo ambiente, sia prima sia dopo la modifica. Anche la richiesta HTTP completa al provider restituisce condizioni attuali e sei giorni. Non è stata eseguita una verifica autenticata dell'endpoint di produzione: la chiamata anonima restituisce correttamente 401. Non sono disponibili qui i log del servizio Render o la sua configurazione runtime. Non è stata modificata la configurazione di produzione.

## Correzione

Il controller restituisce ora 503 e un messaggio utilizzabile per gli errori della dipendenza: 401/403 del provider, 429, altre risposte HTTP, timeout, rete o risposta non valida. I log contengono `Weather provider failure` con operazione, categoria e status esterno/tipo di causa. URL completi, chiavi API, body del provider e messaggi grezzi delle eccezioni non sono registrati. Le risposte non valide non diventano dati meteo in cache.

Il widget mostra il messaggio del backend, incluso il riferimento richiesta già aggiunto da `apiFetch`; un successivo refresh riuscito ripristina le condizioni senza ricaricare la pagina. Non sono introdotti retry automatici verso un provider che rifiuta l'accesso o limita le richieste. Permessi e endpoint rimangono quelli esistenti. La cache rimane in memoria: questa patch non rende persistenti i dati meteo né garantisce la disponibilità del provider durante un deploy.

## Verifica del prossimo errore su Render

Dopo aver distribuito la patch, premere ↻ e leggere il messaggio del widget. Nei log Render cercare `Weather provider failure` all'ora della richiesta:

| Categoria / status | Verifica da eseguire |
| --- | --- |
| HTTP 401/403 | Endpoint e credenziali configurati; accesso dal server al provider |
| HTTP 429 | Limiti del provider e richieste provenienti dall'infrastruttura condivisa |
| HTTP 5xx | Disponibilità del provider |
| TIMEOUT | Latenza e raggiungibilità dal container Render |
| DNS / TLS / NETWORK | Risoluzione, certificati e connessione in uscita |
| INVALID_RESPONSE / EMPTY_RESPONSE | Formato o contenuto restituito dal provider |

Non inserire token, chiavi o header Authorization in screenshot/log condivisi. Lo status 503 esposto da RadioTech è distinto dallo status `upstreamStatus` registrato per il provider.

## Test

Nove test backend passati: cache/validazione esistenti; status esterni 401, 403, 429, 500, 503; timeout; risposta incompleta seguita da recupero; errore della ricerca località. I test verificano anche che credenziali/body esterni non compaiano nei log.

Due test browser passati: funzionalità meteo esistente (località, previsioni, indicatori e responsive) e messaggio di errore seguito dal recupero manuale, anche a 320 px. Build riuscita. Questi test usano un provider simulato; il controllo Java diretto usa il provider reale da questo ambiente e non costituisce una verifica della rete Render.
