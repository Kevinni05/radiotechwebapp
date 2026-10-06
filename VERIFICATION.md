# RadioTech — modifiche e verifica del rilascio

**Aggiornamento del 5 ottobre 2026:** nuove sezioni gestionali, correzioni ulteriori del layout e controlli estesi sono descritti in [ACCEPTANCE-2026-10-05.md](ACCEPTANCE-2026-10-05.md). I risultati e gli hash sotto sono lo storico del 3 ottobre e non identificano gli artefatti ricompilati il 5 ottobre.

Verifica locale del 3 ottobre 2026 sui due progetti. Le modifiche sono nei file di lavoro, senza commit, push o deploy live. Il progetto mobile ha ora un repository Git locale `main`; archivi preesistenti, chiavi di firma, configurazione privata e simboli sono esclusi da Git.

## Modifiche applicate

| Area | Risultato |
| --- | --- |
| Web | Layout responsive, navigazione mobile e laterale senza sovrapposizioni, focus/accessibilità e risorse Leaflet locali; correzione del rendering task, selettori operatori, salvataggio profilo e lettura/salvataggio delle misure RF delle antenne. |
| Restyling finale | Design riscritto: login, dashboard, navigazione, indicatori, tabelle, moduli e workspace; tipografia Manrope locale, icone SVG, animazioni di scroll e transizioni con movimento ridotto. Logo RadioTech trasparente creato al termine e integrato in login/menu/favicon. Vedere [DESIGN.md](DESIGN.md). |
| Funzioni web enterprise | Workspace incidenti/SLA, alert, ricerca, transizioni, certificazioni e scadenze; controlli coerenti con il ruolo. Dati non accessibili non vengono presentati come conteggi pari a zero. |
| AI su web e mobile | Indicatori manutentivi spiegabili, storico approvato e stima degli intervalli; scope personale per operatori, chat con modello centrale reale, contesto operativo facoltativo e CSV sul web. Nessuna probabilità di guasto inventata. |
| Squadra e sicurezza | Turni volontari, pause, disponibilità, richieste di supporto, pericoli e quasi incidenti; presa in carico/risoluzione dei responsabili, versioni e richieste idempotenti. Nessun GPS continuo o diagnosi. |
| Mobile | Tema Material 3, layout home responsive, nuova schermata incarichi con ricerca/priorità/scadenze/revisione, azioni sul campo, apertura incarico dalle notifiche, avvio con errore recuperabile e localizzazione italiana. |
| Sessioni e rete | Refresh token deduplicato sul web e ritentativo autenticato sul mobile; timeout e blocco redirect delle credenziali; controllo del cambio sessione e rimozione del token push al logout. |
| Privacy AI | Conversazioni in memoria, escaping del testo, cronologia limitata; logout/cambio account chiudono le schermate mobile e scartano risposte pendenti. La scadenza della sessione web cancella i dati privati e il rinnovo del token preserva la conversazione corrente. Dati workforce esclusi dal contesto del modello. |
| Offline | Operazioni legate all'identità e al tenant che le hanno create; invio sospeso su account diverso senza consumare tentativi; filtro dello stato sincronizzazione per identità. |
| Sicurezza | Token QR/FCM rimossi dagli elenchi; badge riservato alla gestione utenti; regole Firestore più restrittive sui documenti operatori; idempotenza/transizioni task in transazione; escaping HTML e protezione CSV da formule. |
| Produzione | Profilo fail-closed, origin HTTPS/CORS espliciti, secret separati, CSP nonce, log JSON e request ID, sonde readiness/liveness, container senza privilegi e Compose/Caddy predisposti. |
| Distribuzione | Pipeline web/backend/Firebase/container e pipeline Flutter/Android; firma Android esterna, endpoint release fisso HTTPS, script di build e verifica, documentazione di rollback e accettazione. |

## Risultati eseguiti

| Verifica | Risultato |
| --- | --- |
| Suite backend senza emulatori | 156 test rilevati: 115 eseguiti, 41 esclusi perché richiedono emulatori; zero errori/fallimenti. Include la chiamata AI reale abilitata esplicitamente per questa verifica. |
| Integrazione Firestore/Auth | Tutti i 41 test con emulatori: passati, nessuno escluso. Include workflow sul campo, report, inventario atomico, incidenti, competenze, QR, autorizzazioni, isolamento tenant, turni e privacy delle segnalazioni. |
| Modello AI reale | Ollama 0.35.1 e Qwen3 4B scaricato e avviato; test del servizio Java passato contro il provider reale. Identità di prova nel contesto di sicurezza; non è una prova end-to-end con utente Firebase di produzione o via Internet. |
| Regole Firestore/Storage | 9 test passati, comprese negazione cross-tenant, assenza claim, accesso viewer ai documenti operatori e divieto di accesso client alle nuove collezioni workforce. |
| Chromium/Playwright | 12 test passati: login/401/CSP, task e incidenti/skills, viewport 390 px, permessi viewer, profilo/badge, misure RF, chat/escaping/logout, rinnovo/scadenza della sessione, turni/segnalazioni, nuovo login a 320 px, font/logo locali, accessi rapidi, movimento ridotto e focus in menu/dialoghi. API applicative simulate; la barriera HTTP non autenticata è reale. |
| Flutter | `flutter analyze`: nessun problema; 42 test passati, inclusi errori/retry, schermo piccolo, coda offline, HTTP, report, nuove sezioni e chiusura della chat su logout/cambio account con risposta pendente. |
| Android | APK debug ricompilato con le nuove sezioni e il controllo delle sessioni. Artefatto QA, non destinato allo store. |
| Dipendenze JavaScript | `npm audit`: zero vulnerabilità rilevate nelle dipendenze del repository. Non comprende tool globali, Java e Flutter. |
| Riproducibilità backend | Due ricostruzioni del JAR finale con SHA-256 identico. |
| Controlli statici aggiuntivi | Sintassi JavaScript, parsing degli script PowerShell e `git diff --check`: superati. |

Le prove Firebase hanno usato soltanto `demo-radiotech` con emulatori locali. Nessun dato di produzione è stato scritto o migrato. I conteggi delle due esecuzioni Java sommano i test eseguiti: **115 + 41 = 156**, non 156 test ordinari più 41 aggiuntivi. Per evitare una porta locale già occupata, le ultime prove Firebase hanno usato una configurazione QA isolata in `build/firebase.qa.json`.

## Artefatti

| Artefatto | Percorso | SHA-256 |
| --- | --- | --- |
| API e web | `build/libs/radiotech.jar` | `53FC53D65C854FD23C4C98B9D1FE9A639AA25E23639FD68E4786E86CEA4CE33B` |
| Android QA | `gestionale_radio/build/app/outputs/flutter-apk/app-debug.apk` | `60EF6A1FB7CCCC9267E9DB4725AE91949F2BFF12559B6E3E77A7886B8B9CA835` |
| Logo RadioTech | `src/main/resources/static/assets/brand/radiotech-symbol-v1.png` | `B05211F6C784E3D92206C29F6606BD867AD5BA763233BD642507D972943E8126` |

I log e i riepiloghi locali sono in `radiotechwebapp/build/` e nei file `qa-*.log` dell'app mobile; sono esclusi da Git. Le catture responsive sono in `build/reports/web/`. Gli hash descrivono questi artefatti specifici e vanno aggiornati dopo ogni modifica.

## Passaggi di produzione ancora necessari

Il progetto Firebase e Firestore esistono. Il sito `gestionale-radio.web.app` restituisce 404 sull'API; Cloud Run e Compute Engine risultano disabilitati nel progetto verificato e `billingEnabled=false`. Il dominio/server backend e la chiave di upload Android esistente non sono stati identificati nelle configurazioni disponibili. La chat funziona contro il provider reale locale; il pacchetto di deploy configura modello e backend centrali, ma **il servizio pubblico non è stato attivato**.

La build Docker deve passare sulla CI o su un host con Docker, assente in questa workstation. Una build iOS richiede macOS/Xcode e provisioning; non è stata eseguita su Windows. Le pipeline sono predisposte, ma non sono state avviate su GitHub.

Servono rollout staging, configurazione dei secret/IAM/DNS/TLS, verifica delle regole e degli indici sui dati esistenti, firma release e prove con utenti/dispositivi reali. Il rate limiter è per istanza, l'API REST non applica ancora App Check e le bozze/allegati locali non hanno cifratura applicativa. Questi limiti non sono nascosti da un'etichetta enterprise e devono essere risolti o gestiti prima di affermare requisiti ulteriori di scala o protezione dei dispositivi.

Seguire [ENTERPRISE_AI.md](ENTERPRISE_AI.md) per le nuove sezioni e il modello centrale, [DEPLOYMENT.md](DEPLOYMENT.md) per il server e `gestionale_radio/RELEASE.md` per l'app. Le previsioni sono indicatori spiegabili basati sullo storico, non un modello di guasto ML validato. Sono documentati campionamento, limiti per istanza, retention e dimensionamento. Questa verifica dimostra i risultati elencati; non certifica ogni possibile sezione, dispositivo, carico o scenario di produzione e non sostituisce un penetration test.
