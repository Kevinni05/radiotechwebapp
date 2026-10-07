# Box ora e meteo operativo

La dashboard mostra la data italiana estesa (es. 7 ottobre 2026), l’ora HH:mm:ss aggiornata ogni secondo e un orologio analogico con lancetta corallo. L’ora è quella del dispositivo; il fuso orario è indicato.

Il campo Cerca paese o antenna propone le antenne con coordinate disponibili e le località restituite dalla ricerca geografica. Cerca è disponibile anche da tastiera. Scegli antenna elenca gli impianti; La mia posizione usa la geolocalizzazione se autorizzata nel browser. La selezione rimane memorizzata nel browser. Non è necessario condividere la posizione per cercare una città.

Il box riporta temperatura, condizioni in italiano, temperatura percepita, umidità, vento, alba, tramonto, UV, fuso della località e aggiornamento. Le previsioni mostrano i cinque giorni successivi a oggi, con minime, massime, condizioni e probabilità di pioggia. I dati mancanti sono indicati con un trattino e gli errori permettono di riprovare con Aggiorna.

Il backend interroga Open-Meteo con timeout e cache: meteo 10 minuti, ricerca 1 ora. Il browser evita richieste ripetute e ignora le risposte di località precedenti. La griglia si adatta a telefono, tablet e desktop. Attribution Open-Meteo visibile nel box; endpoint e chiave meteo già configurabili per il futuro servizio commerciale.

Verifiche: suite backend, test dedicato a previsioni/ricerca/cache/coordinate; prova autenticata sul backend reale con ricerca di Lecce e cinque giorni di previsioni.

Ricontrollo browser superato: 4 scenari, comprendenti tutte le sezioni a sette larghezze, protezione delle note del calendario, ricerca/località/ora e box meteo da 320 a 1440 pixel. Esiti in .dist/weather-widget-web-recheck.log; backend reale in .dist/weather-widget-live.log. Servizio aggiornato e salute HTTPS verificata.


## Box compatto e indicatori operativi

Le previsioni partono chiuse e si aprono tramite Mostra; Nascondi le comprime nuovamente. La scelta rimane durante il cambio sezione e gli aggiornamenti del meteo, senza espansioni automatiche. Ogni nuovo caricamento parte compresso.

Orologio e informazioni meteo occupano meno spazio. Temperatura percepita, umidità, vento, alba, tramonto e UV sono raccolti in indicatori piccoli. Quattro widget cliccabili riportano incarichi attivi, incarichi scaduti, report da verificare e scorte basse, ricavati dai dati già caricati per l’account corrente. Aprono le rispettive sezioni senza nuove richieste al database.

Torna in alto scorre il documento fino a zero: l’header fisso non viene più usato come destinazione. Il comportamento rispetta il movimento ridotto e viene usato anche per i collegamenti del footer.

Verifiche del box compatto superate: ricerca meteo e footer, indicatori con dati reali della fixture, previsioni chiuse/apertura/chiusura, larghezze 320–1440 pixel e scorrimento fino a quota zero sia con animazione sia con movimento ridotto. Log .dist/compact-widgets-web.log e .dist/compact-widgets-recheck.log.


## Nuovo design del pannello

Ridisegnato come un pannello strumenti su fondo grafite: orologio con data e fuso, meteo con temperatura in evidenza e icona vettoriale, indicatori operativi separati da divisori sottili. Gli accenti caldi sono limitati all’orologio e all’icona meteo.

Cambia località apre il campo di ricerca e i comandi per antenne/geolocalizzazione; selezionare una posizione richiude il pannello. Aggiorna rimane accessibile dall’icona con etichetta. Le previsioni restano chiuse all’avvio e tutte le funzioni precedenti rimangono disponibili. I valori e le unità meteorologiche restano insieme, senza spezzarsi su più righe.

Stili isolati in assets/environment-console.css per evitare modifiche agli altri box della console.
