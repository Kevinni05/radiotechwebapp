# RadioTech Enterprise Pro: implementazione e verifica

Ramo: `feature/enterprise-pro-complete-ui`. Base: `origin/main` (`a4595a16598590c5246125b69340ec74f3a0c347`). Nessuna modifica a `gestionale_radio`.

## Esperienza e sezioni

La topbar segue la struttura del riferimento fornito: contesto a sinistra, ricerca centrata, account a destra. Ricerca e scorciatoia Ctrl/Cmd+K aprono una palette di sezioni e azioni effettive, filtrata dai permessi; notifiche, aggiornamento, creazione rapida e menu account mantengono le azioni esistenti. Su mobile la barra resta su una riga. Menu e dialoghi supportano tastiera, Escape e ritorno del focus.

| Area | Intervento |
| --- | --- |
| Panoramica | Gerarchia compatta, KPI responsive, rete prima degli appunti, mappa con altezza stabile, distribuzione incarichi su due colonne |
| Infrastruttura | Filtri etichettati, heading e contenitori responsive |
| Incarichi e agenda | Composizione e lista allineate a desktop, impilate su mobile; calendario e flussi esistenti conservati |
| Centro report | Titolo leggibile, operatore/data/ID separati; approvazione ed eliminazione accessibili nella riga compressa |
| Operatori e carico squadra | Layout e toolbar comuni, etichette dei campi |
| Inventario e approvvigionamenti | Filtri con gerarchia chiara, tabelle contenute, composizione e liste responsive |
| Comunicazioni | Storia compatta e testi lunghi contenuti |
| Strumenti RF, rischi, squadra, AI, Enterprise Pro | Contenitori e heading condivisi; funzioni e moduli esistenti conservati |
| Profilo e stato sistema | Contenimento responsive e heading dello stato prima del monitor |
| Login e portale clienti | Geometria consolidata, campi associati a label, stato di accesso annunciato, prevenzione dei duplicati e focus dopo errore/accesso/logout |

L'audit comprende le 18 viste effettivamente generate nella navigazione, oltre al login e alle due condizioni del portale. Alcune sezioni ricevono il layout condiviso senza riscrittura della logica del modulo.

## Note rapide di sessione

Widget separato dalle note personali del calendario. Autosalvataggio a 350 ms, limite 8.000 caratteri, stato e ultima modifica, conferma prima di svuotare. Usa esclusivamente `sessionStorage`, con chiave composta da tenant e identità. Nessun invio delle note al backend e nessuna persistenza delle nuove note in `localStorage`.

Refresh e navigazione conservano il testo nella stessa scheda; logout cancella la chiave dell'identità attiva e il testo; cambio identità/tenant annulla salvataggi pendenti e carica soltanto i propri appunti. Errori dello storage sono annunciati e il testo resta recuperabile nell'editor. La durata dipende dalla sessione della scheda: il ripristino delle schede del browser può ripristinare anche il suo storage. Il widget invita a evitare credenziali e informazioni sensibili.

## Rimozione dei report e distribuzione

`DELETE /api/reports/{id}` e `/api/v1/reports/{id}` richiedono `REPORT_DELETE`, identità autenticata e tenant coerente. I ruoli gestionali autorizzati coincidono con la policy di approvazione. Il server registra `removedAt`, `removedBy` e un evento `REPORT_REMOVED` nella stessa transazione Firestore. Le richieste ripetute conservano il primo evento.

La rimozione è logica in tutti gli stati: PDF, allegati, firma, hash, stato, incarico e movimenti di inventario restano conservati. Liste, pagine e conteggi mostrano solo i report attivi; il recupero diretto autorizzato mantiene l'evidenza. Una revisione nuova di un report rimosso viene rifiutata. Un'approvazione già avviata in `APPROVAL_PENDING` può completare il consumo di inventario, evitando operazioni parziali abbandonate.

La paginazione conserva i documenti legacy privi di `removedAt` e legge al massimo dieci batch per richiesta. Una pagina vuota può avere un cursore successivo: l'interfaccia permette di proseguire. I conteggi usano aggregazioni, senza scaricare l'intero archivio.

**Prima della distribuzione su Firebase:** pubblicare i tre nuovi indici in `firestore.indexes.json` tramite il normale processo di deploy (tenant/rimozione, tenant/stato/rimozione, tenant/operatore/rimozione). Gli emulatori non provano la disponibilità degli indici di produzione. Nessun deploy o modifica di dati di produzione è stato eseguito.

## CSS e file

`radiotech-theme.css`, `design-system.css`, font e risorse del branding restano identici alla base. `enterprise-layout.css` consolida il layout condiviso; `signature-workspace.css` e `.js` isolano topbar e note. Nessuna dipendenza aggiunta. CSS di operatività, report e portale sono aggiornati insieme ai rispettivi template; un'icona di ricerca è aggiunta al registro esistente. Backend: controller, modello, permission/policy, service, indici. Test: nuovo controller/emulatore, fixture condivisa e suite web dedicate. `scripts/run-firestore-tests.cjs` include la nuova suite nel comando ordinario.

## Validazione

- Build Java 21 / Spring Boot completata.
- Suite ordinaria backend: 226 rilevati, 147 passati, 79 saltati, zero errori/fallimenti. I saltati includono test che richiedono emulatori e il test opzionale AI reale.
- Suite completa Firebase Firestore/Auth: 78 passati, zero saltati/errori/fallimenti. Include i cinque nuovi scenari di rimozione. I conteggi si sovrappongono alla suite ordinaria.
- Verifica mirata controller/report/materiali: 19 passati, zero saltati/errori/fallimenti.
- Regole Firestore/Storage: 10 passati. Strumenti Telco: 3 passati.
- Web Chromium: suite completa 70 passati, zero fallimenti. Dopo gli ultimi aggiustamenti mobile, le 21 verifiche di report/workspace sono state rieseguite: tutte passate, zero fallimenti.

Test web coprono permessi, ciclo di vita dei report, conferma/cancellazione, errori 403, retry, prevenzione dei duplicati, focus, note/refresh/logout/cambio tenant, storage non disponibile, palette e comandi reali. Le API sono simulate in browser; transazioni e policy sono verificate separatamente negli emulatori.

## Evidenze visive e riproduzione

Risoluzioni: 1920×1080, 1440×900, 1280×800, 1024×768, 768×1024, 430×932, 390×844, 375×812, 320×720.

Le acquisizioni usano dati di prova pieni/vuoti e bloccano le risorse HTTPS esterne: basemap e meteo remoto non sono quindi una verifica dei relativi provider. Il controllo misura overflow orizzontale ed errori JavaScript e salva screenshot dell'intera pagina; la revisione manuale riguarda campioni desktop/tablet/mobile e stati dei componenti. Non equivale a un audit WCAG completo o a una verifica di ogni pixel.

Sono stati generati 378 screenshot prima e 378 dopo (18 viste × 9 risoluzioni × 2 stati, più login/portale), oltre a 12 screenshot dei componenti. Audit baseline/finale e login/portale: zero errori JavaScript, errori di acquisizione e overflow di pagina; tabelle e calendario mantengono scroll interno dove necessario. Dashboard e report sono stati riacquisiti dopo le rifiniture finali: 36 acquisizioni, zero errori e overflow.

Campioni inclusi per la revisione: [dashboard prima](screenshots/enterprise-pro/dashboard-before.png), [dashboard dopo](screenshots/enterprise-pro/dashboard-after.png), [report prima](screenshots/enterprise-pro/reports-before-mobile.png), [report dopo](screenshots/enterprise-pro/reports-after-mobile.png), [riga compressa](screenshots/enterprise-pro/reports-collapsed-mobile.png), [conferma eliminazione](screenshots/enterprise-pro/report-delete-mobile.png), [ricerca desktop](screenshots/enterprise-pro/command-desktop.png), [portale mobile](screenshots/enterprise-pro/portal-mobile.png).

Artefatti completi nell'ambiente: `/workspace/.setup/visual/baseline`, `/workspace/.setup/visual/final`, `/workspace/.setup/visual/components`; log e audit JSON sono nella stessa directory. Il baseline delle 18 viste è stato acquisito prima delle modifiche. Login e portale baseline sono ricostruiti dalle risorse originali di `origin/main` usando il server locale con Firebase disabilitato.

Per ripetere la matrice delle 18 viste, avviare il JAR compilato e poi:

```bash
VISUAL_BASE_URL=http://127.0.0.1:18080 \
VISUAL_OUTPUT=.dist/enterprise-pro/after \
node scripts/capture-enterprise-ui.mjs after
```

Usare due server o due versioni distinte per il confronto prima/dopo. Non sostituire il JAR di un server in esecuzione. Per l'anteprima di template su filesystem disabilitare la cache Thymeleaf. Il controllo responsive della mappa verifica anche che la sua altezza non collassi per effetto del layout flex.

## Limiti e consegna

Nessun browser diverso da Chromium o servizio di produzione è stato verificato. Nessuna migrazione distruttiva. La PR dipende dall'accesso a `api.github.com`: il dominio è stato aggiunto alla bozza di configurazione cloud; salvarla non applica automaticamente la modifica al runtime. L’API ha poi risposto correttamente; ramo, commit e PR sono riportati nella consegna finale.
