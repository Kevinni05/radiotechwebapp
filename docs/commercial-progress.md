# RadioTech — backlog unico di consolidamento

Aggiornato il 12 ottobre 2026. [Piano originale](commercial-consolidation-plan.md), [registro storico](commercial-progress-history.md), [costi e handover](COMPANY-COSTS-AND-HANDOVER.md).

Il progetto corrente è un **test**, per scelta del proprietario. Billing è già abilitato sul progetto esistente; la verifica read-only ha trovato zero bucket. Non vengono attivati nuovi abbonamenti, Storage o Functions. Questa tabella sostituisce gli stati precedenti: download, outbox e controller feature erano ancora indicati come mancanti dopo la loro implementazione.

**Il gate commerciale resta aperto.** Implementazione, test e accettazione aziendale sono evidenze diverse. Frontend consolidato: [web PR36](https://github.com/Kevinni05/radiotechwebapp/pull/36), [mobile PR27](https://github.com/Kevinni05/gestionale_radio/pull/27). La precedente CI web ha superato 123 casi per ciascuno dei tre browser, backend, emulatori, JAR riproducibile e container. Questa iterazione aggiunge distribuzione autenticata, aggregazioni task e collaudi fisici/API.

| ID | Implementazione ed evidenza | Accettazione restante |
|---|---|---|
| B01 | Backup cifrato/offsite e restore isolato documenti, claims e allegati già riusciti; nuova verifica APK e heap dedicato al tool | Password/provider/MFA produzione; recupero chiave aziendale; RPO/RTO misurati |
| B02 | Policy ruoli/tenant, ENGINEER distinto dall'amministratore; prove API reali di tenant estraneo, ruolo e account revocato | Regole e indici pubblicati nel test, con letture negative reali; restano matrice cliente e App Check/MFA |
| B03 | Transizioni atomiche, replay e riconciliazione; doppia approvazione reale con un consumo | Esercitazione guasti nell'ambiente cliente |
| B04 | Audit atomico e intenti Auth durevoli con recovery | Retention e nomina responsabili |
| B05 | Chiavi persistenti, tre retry testati; telefono conserva coda, bozza e ricevuta dopo riavvio e upgrade | Urgente/FCM fisico con rete degradata |
| B06 | Filtri/cursor/cache tenant; aggregazioni storico task con fallback legacy; fixture 10.010 task trasferisce 10 documenti attivi | Benchmark globale fatturato e p95: 99,9% riguarda documenti task trasferiti, non letture globali fatturate |
| B07 | Streaming con stage/digest prima degli header, limiti/concorrenza, cleanup su cancellazione; riferimenti atomici; migrazione/checkpoint/rollback e cleanup dry-run | Nuovo bucket/migrazione reale rinviati alla configurazione cliente; carico/retention approvati |
| B08 | Task/calendario/PRO in outbox; retry per destinatario, lease, backoff, dead letter e allarme | FCM fisico; semantica almeno una volta dopo accettazione FCM |
| B09 | Errori strutturati/requestId, deadline, risposte limitate, quote e concorrenza | Proxy/repliche e misure contrattuali |
| B10 | Snapshot tecnico/versione/checksum/unità e PDF Flutter reale; replay, approvazione, rinomina e rimozione motivata provati via API | Modelli report approvati dal cliente |
| W01 | Token e controlli condivisi, dialog/form coerenti | Revisione visuale cliente |
| W02 | Dieci moduli feature, calendario separato e build esbuild in CI/container | Manutenzione ordinaria |
| W03 | Shell, azienda/ambiente, ricerca, notifiche/account/sync, tastiera | Etichette ambiente cliente |
| W04 | KPI con significato/timestamp e drill-down filtrati | Utilità con operatori reali |
| W05 | Ordine/pannelli responsive e senza vuoti forzati | Contenuti cliente |
| W06 | Centro report/PDF/materiali, azioni protette, rimozione motivata | Workflow aziendale |
| W07 | Antenna verde, badge/legenda testuale, lista↔mappa, zoom conservato | Dati territoriali cliente |
| W08 | Calendario responsive, filtri/note, tastiera/touch e protezione edit | Revisione cliente |
| W09 | Filtri server, cursori e stati UI; ritorno e pagine caricati conservati | Carico cliente |
| W10 | Refresh selettivo, deduplica, tab nascosta e protezione focus/form | Monitor esercizio |
| W11 | Focus/dialog/zoom/tastiera verificati nei browser | Lettura assistiva manuale |
| W12 | TOTP QR/reauth e form guidati senza prompt nativi | Enrollment/recovery cliente |
| W13 | Firebase persistence gestita, guardie sessione e logout/multi-tab | Policy aziendale |
| W14 | Ricerca autorizzata bounded e filtri salvati; proiezioni/backfill pronti ma non attivati | Utilità cliente; eventuali Functions nel futuro progetto |
| M01 | Asset/incarico/note e azione principale dopo scansione | Camera sul campo |
| M02 | Checklist nel report, versione/unità/checksum condivisi con PDF | Checklist cliente |
| M03 | Stato GPS, accuratezza/permessi/retry; check-in server con coordinate QA | GPS fisico e policy personale |
| M04 | Bozza, preview, queue/ricevuta e conferma server | Firma/foto sul campo |
| M05 | SQLite AES-GCM/Keystore e isolamento; telefono seed→force-stop→restart→upgrade 11000→11001 riusciti | Hardware Keystore e recupero dispositivo |
| M06 | Resume/rete/retry/session guard, conflitti/capienza e background documentato | Rete degradata nel pilot |
| M07 | Home/report/tools feature/repository/Riverpod; richieste report attraverso repository | Evoluzione ordinaria |
| M08 | Compressione foto, limiti PDF/download e produzione PDF Flutter reale | Foto reali su dispositivo medio |
| M09 | Touch/textScale 2 e layout verificati | TalkBack e uso al sole |
| M10 | Deep link/deduplica/sessione/logout e ricevute persistenti | FCM cold start sul telefono |
| M11 | Firma originale, versionCode canonico 11002; updater privato autenticato al solo origin API; digest/appID/firma verificati. Telefono aggiornato 10077→11002, riapertura/sessione e controllo privato riusciti | Consegna e installazione presso l'azienda; prova del download/installatore nativo di una release successiva |
| M12 | Samsung fisico: persistenza/riavvio/upgrade QA; Android 34 emulato: CI persistenza già riuscita | Camera, GPS, firma, FCM, App Check e rete degradata su fisico+emulatore |
| M13 | Cache pack offline con freschezza, checklist per tipo e scorciatoie | Utilità/dimensioni cliente |

Il canale privato usa lo stesso HTTPS RadioTech: `/api/v1/distribution/latest.json` e APK protetti da login e tenant. Sistema mostra la versione e verifica il digest scaricato. Il codice nativo trasmette il bearer soltanto all'origin API e verifica firma/appID prima dell'installatore. Nessun avviso viene inviato automaticamente.

Proposta: licenza annuale non esclusiva, infrastruttura dedicata intestata all'azienda, supporto separato. Budget, retention e responsabilità sono [proposte](COMPANY-COSTS-AND-HANDOVER.md), non contratti firmati. Pilot di due settimane, privacy GPS, nominativi effettivi e firma dell'handover richiedono l'acquirente e restano aperti.
