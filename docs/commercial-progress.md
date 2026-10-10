# RadioTech — registro di consolidamento commerciale
Aggiornato il 10 ottobre 2026. Piano di riferimento: [piano completo](commercial-consolidation-plan.md).
Repository mobile: [gestionale_radio](https://github.com/Kevinni05/gestionale_radio).

## Stato della release
**Gate di vendita NON superato.** Le modifiche riportate qui sono circoscritte; nessuna CI verde certifica l'intero piano. "Parziale" significa che almeno un criterio resta aperto. "Da validare" significa che la base esiste, ma non è stata dimostrata l'accettazione del piano.

## Modifiche ed evidenze
- Backup: [PR13](https://github.com/Kevinni05/radiotechwebapp/pull/13), [export e restore reali riusciti](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089481829). Archivio cifrato, 137 documenti, 5 identità/claims, 12 allegati Firestore. Restore su emulatori con account disabilitati; non prova recupero di password/provider/MFA in produzione. Storage non configurato: nessun oggetto storage verificato.
  - testedAt: 2026-10-10T21:57:54.530202343Z
  - SHA256 archivio: d56355f4d36c061e3beda8901ba46adb1c09c52da3317b84033986381d248109
  - Retention artefatto: 90 giorni; non sostituisce copia e chiave sotto ownership del gestore.
- Monitor: [run riuscito](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38090130601). La prova del backup deriva dal restore sopra, non dal solo health.
- ENGINEER: [web PR14](https://github.com/Kevinni05/radiotechwebapp/pull/14), [mobile PR20](https://github.com/Kevinni05/gestionale_radio/pull/20). Accesso gestionale coerente con NETWORK_MANAGER, senza amministrazione account o identità operatore implicita. Regole repository testate; deploy delle regole effettive ancora da verificare.
- Urgente/report: [mobile PR19](https://github.com/Kevinni05/gestionale_radio/pull/19), [CI riuscita](https://github.com/Kevinni05/gestionale_radio/actions/runs/38089588769). Chiave e payload persistiti prima della rete; ricevuta distinta dal report; retry riusa la stessa operazione; bozza corrotta non viene trattata come vuota. Test di tre retry con risposta persa; prova reale di riavvio dispositivo ancora aperta.
- Offline: PR19 impedisce al pannello di mostrare operazioni senza identità o appartenenti a utente/tenant diversi; test negativi espliciti.
- Refresh: [PR15](https://github.com/Kevinni05/radiotechwebapp/pull/15), [CI riuscita](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089621209). Solo vista attiva, richieste concorrenti deduplicate, sospensione tab nascosta e protezione edit/focus.
- Rate limit: [PR16](https://github.com/Kevinni05/radiotechwebapp/pull/16), [CI riuscita](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089626012). Alias /api e /api/v1 e riferimenti verifica report condividono budget. Replica/proxy reale non provati.
- Audit report/materiali: [PR17](https://github.com/Kevinni05/radiotechwebapp/pull/17), [cinque job CI riusciti](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089758505). Eventi nello stesso batch/transaction della mutazione, identità esplicita, replay senza eventi/consumi doppi; consumo fallito senza audit di successo.
- Notifiche: [PR18](https://github.com/Kevinni05/radiotechwebapp/pull/18), [cinque job CI riusciti](https://github.com/Kevinni05/radiotechwebapp/actions/runs/38089933072). Ricevute per token con hash, retry solo falliti, PARTIAL, lease, backoff/jitter, DEAD_LETTER; rimozione effettiva UNREGISTERED. I vecchi invii immediati non sono tutti migrati. FCM può accettare prima di un errore di persistenza: resta semantica almeno una volta.
- Audit identità: [PR19](https://github.com/Kevinni05/radiotechwebapp/pull/19). Intento durevole prima di Firebase Auth, conferma sincrona, PENDING dopo interruzione. Non implementa ancora riconciliazione e allarme dei pendenti.
- Updater personalizzato: [mobile PR21](https://github.com/Kevinni05/gestionale_radio/pull/21). Identità installata e nome app nativi, stesso contratto filename del workflow, rifiuto app diversa e firma incompatibile. Il digest dei byte scaricati non viene verificato nell'app.

## Backlog unico — backend
| ID | Stato | Criteri ancora aperti |
|---|---|---|
| B01 | Parziale, recovery dei dati attuali provato | Recovery produzione completo di identità, chiave separata recuperabile, owner, misure RPO/RTO, storage e riconnessione client |
| B02 | Parziale | Matrice completa ruolo×endpoint×tenant e riferimenti indiretti; deploy/verifica regole Firebase; revoca e sospensione su staging |
| B03 | Da validare | Crash dopo ogni fase, concorrenza approvazione/rimozione e riconciliazione con riavvio reale |
| B04 | Parziale | Tutti i cambi identità/azioni critiche; retry/riconciliazione/allarme PENDING, retention e accesso definiti |
| B05 | Parziale | E2E tre retry e riavvio su Android, un solo segnale e un solo report; integrità ricevute/bozze dopo upgrade |
| B06 | Aperto | Baseline, aggregazioni/proiezioni, invalidazione, viewport, prova riduzione letture ≥70% |
| B07 | Aperto | Configurare storage, migrazione dry-run/checkpoint/digest/rollback, streaming e carico concorrente, cleanup e retention |
| B08 | Parziale | Migrare invii immediati, allarme dead letter, cleanup retry, deduplica/deep link/logout e device freddo |
| B09 | Parziale | DTO/errori/deadline/concorrenza, proxy e repliche, timeout/quote e vecchio mobile integrati |
| B10 | Da validare | Snapshot report/PDF immutabile, revisione/rifiuto tracciati e coerenza checklist/materiali con rinomina dati |

## Backlog unico — web
| ID | Stato | Criteri ancora aperti |
|---|---|---|
| W01 | Da validare | Inventario e consolidamento componenti/token e varianti condivise |
| W02 | Aperto | Estrarre feature dal template mantenendo regressioni verdi |
| W03 | Da validare | Tastiera, distinzione staging/produzione e shell con dati reali |
| W04 | Da validare | Significato KPI, timestamp e drill-down; evitare disponibilità gestionale confusa con uptime |
| W05 | Da validare | Ordine/pannelli e contenuti realistici su desktop/tablet/mobile |
| W06 | Da validare | PDF/materiali, tastiera, rimozione motivata e operazioni concorrenti |
| W07 | Da validare | Simbolo/legenda accessibili e selezione bidirezionale reale |
| W08 | Da validare | Nomi lunghi, note, touch/tastiera e overflow |
| W09 | Da validare | Cursor e filtri server per ogni tabella, ritorno dal dettaglio e stabilità pagina |
| W10 | Implementato e testato in CI | Verifica autenticata con dati reali e dipendenze di produzione |
| W11 | Da validare | Tastiera, focus/dialog, contrasto, zoom 200%, lettura errori |
| W12 | Aperto | Sostituire prompt MFA, QR/reauth/recovery guidati, configurazione effettiva |
| W13 | Aperto | Decisione e implementazione sessione, logout/multi-tab; CSRF se cookie |
| W14 | P2 aperto | Ricerca/filtri salvati autorizzati e utilità cliente |

## Backlog unico — mobile
| ID | Stato | Criteri ancora aperti |
|---|---|---|
| M01 | Da validare | Asset/dati/note/incarico nella prima schermata reale |
| M02 | Da validare | Checklist diretta unica e stessa versione report/PDF |
| M03 | Da validare | GPS/accuratezza/distanza/retry e deroga autorizzata su device |
| M04 | Parziale | Invio urgente recuperabile; completare/validare tutte le fasi upload/report/preview/ricevuta |
| M05 | Aperto | Database transazionale cifrato, migrazione senza perdita e chiavi protette; SharedPreferences resta |
| M06 | Da validare | Avvio/resume/rete, conflitti, capienza e comportamento background dichiarato |
| M07 | Aperto | Feature/repository/stato Riverpod, separazione widget e operazioni |
| M08 | Da validare | PDF/foto realistici, tempi/memoria su dispositivo medio |
| M09 | Da validare | Schermi piccoli, sole, textScale 2, TalkBack e tastiera |
| M10 | Parziale | Server retry parziale; deduplica/deep link/foreground/background/logout su due dispositivi |
| M11 | Parziale | Updater personalizzato; byte digest, canale commerciale, versione minima, upgrade e rollback reali |
| M12 | Bloccato dalla mancanza di device/sessione | Due Android reali, QR/GPS/foto/firma/FCM/App Check e rete degradata |
| M13 | P2 aperto | Cache pack, checklist tipo impianto e freschezza offline |

## Registro delle prove integrate
1. Due tenant/ruoli: alcune prove emulatori e browser presenti; matrice completa non chiusa.
2. Web→mobile→QR→check-in→report/allegati→approvazione→magazzino: E2E autenticata reale non eseguita.
3. Rete persa/upload/timeout dopo commit/chiusura forzata: simulazioni mirate presenti, device reale aperto.
4. Urgente con risposta persa: test PR19 riuscito; device/restart e report end-to-end aperti.
5. Approvazione/rimozione concorrenti: acceptance completa aperta.
6. Logout A/login B: pannello e scope testati; coda/bozze/device reale aperti.
7. Revoca/sospensione/cambio ruolo: audit intenzione testato; E2E reale aperta.
8. FCM parziale/token scaduti: test emulatori riuscito; FCM e avvio a freddo reali aperti.
9. Upgrade firmato conserva coda/bozze, firma errata respinta: aperto.
10. Restore dati/claims/allegati attuali isolato riuscito; ripristino identità produzione e client aperti.
11. Meteo/quota: test esistenti non equivalgono alla prova integrata richiesta; aperto.
12. Render SHA/artifact/API approvati: non verificato; canale Android aggiornato da verificare tramite run CI.

## Gate esterni e consegna
Non esporre segreti in issue, log, PR o documentazione. Servono accessi autorizzati ai progetti Firebase/Render e uno staging isolato prima di applicare migrazioni/regole o fare prove con carico.
- Confermare/deployare regole e indici e verificare IAM, App Check enforcement, billing/quote/alert, ownership e backup chiavi.
- Identificare revisione e artefatto effettivi su Render; smoke autenticato prima/dopo deploy.
- Eseguire collaudo Android su almeno due dispositivi e prova upgrade con dati locali.
- Misurare carico concordato (50 utenti/1.000 asset/10.000 report), p95/p99, memoria, letture, sincronizzazione, crash e costi; nessun target è già certificato.
- Provisioning secondo ambiente, dati demo sintetici, export/reimport byte+digest, manuali verificati con utente nuovo, simulazione supporto.
- Scegliere licenza/cessione, ambiente/canale Android, responsabilità e owner supporto; revisione privacy/GPS/foto/firme/retention e licenze terze parti.
- Tre scenari economici, budget/alert e handover account/firma/backup.
- Pilota almeno due settimane o campione equivalente esplicitamente concordato; registro incidenti/feedback.
