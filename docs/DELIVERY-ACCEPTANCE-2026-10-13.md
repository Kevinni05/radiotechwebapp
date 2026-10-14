# Consegna e verbale di accettazione — RADIO TECH

Pacchetto preparato su richiesta di Kevin, con riferimenti da compilare. Stato: candidata tecnica, **accettazione commerciale non rilasciata**. Registro [piano 2.1](ENTERPRISE-PLAN-2026-10-13.md); manuali e recovery in [OPERATIONS-AND-DELIVERY](OPERATIONS-AND-DELIVERY.md).

| Campo | Valore da completare prima della consegna |
|---|---|
| Cliente, tenant e ambiente | ____________________ |
| Responsabile prodotto / accettazione | ____________________ |
| Hosting, fatturazione e sostituto | ____________________ |
| Backup, chiave di recupero e sostituto | ____________________ |
| Firma Android, copia cifrata e sostituto | ____________________ |
| Assistenza: indirizzo o portale | ____________________ |
| Orari e tempi di risposta concordati | ____________________ |
| Escalation incidente critico | ____________________ |
| Referente privacy / retention approvata | ____________________ |
| Budget mensile e destinatario alert | ____________________ |
| Periodo pilota / partecipanti | ____________________ |
| Versione mobile e codice | 1.5.5 / 11004, candidata da verificare |
| Commit web/API/mobile e digest APK/AAB | [Manifest verificato 11004](RELEASE-CANDIDATE-11004.json) |
| Revisione backend e ultimo restore | Da verificare alla consegna |

Non inserire password, token o chiavi nel verbale. Testare il recupero degli accessi con il referente e senza l'account personale dello sviluppatore.

## Registro prove integrato

Per ogni caso annotare: data/ora, esecutore, commit/revisione/build, ID operazione, risultato atteso/effettivo, evidenza privata e difetti. Campo vuoto significa **non verificato**, non superato.

| Caso | Prova da eseguire | Esito / evidenza |
|---|---|---|
| T01 | Dashboard assegna → mobile → QR/GPS → checklist/report → approva → stock | Assegnazione nei tre stati, anteprima GPS annullata e foto/PDF QA riusciti; QR/geofence e flusso completo sul campo ancora aperti |
| T02 | Rete assente; timeout dopo commit; tre retry con stesso ID | ____________________ |
| T03 | Termina processo / riavvia con bozza e coda | QA Samsung riuscita 14/10, incluso upgrade debug 11004→11005; commerciale da verificare |
| T04 | Due approvazioni contemporanee, un consumo | ____________________ |
| T05 | Rimozione report in revisione/approvato, audit e stock invariato | ____________________ |
| T06 | A esce → B entra, nessuna bozza/coda/push di A esposta | Samsung QA: logout A/login B, ricevute e inbox distinte; separazione bozza/coda coperta automaticamente, scenario integrato completo residuo |
| T07 | Tenant B: ID, allegato e QR respinti | ____________________ |
| T08 | Foreground/background/crash/rete persa; Samsung + emulatore | ____________________ |
| T09 | Push parziale, token invalido, permesso negato, cold start | Samsung QA: PARTIAL/NO_TOKENS → registrazione B → retry stesso ID SENT; cold start riuscito. Token invalido e permesso negato fisici residui |
| T10 | Revoca account/dispositivo e cambio ruolo, recovery | Revoca A dalla dashboard, 401/schermata e nuovo accesso riusciti; SDK B passa da lettura consentita a permission-denied dopo revoca emulata; 17 prove regole. Revoca account/cambio ruolo distribuiti residui |
| T11 | Upgrade senza disinstallare, coda/bozze e firma conservate | Recovery APK QA debug riuscita; upgrade firmato commerciale ancora aperto |
| T12 | Restore isolato dati/allegati/identità, digest e RPO/RTO | ____________________ |
| T13 | Quota/timeout/meteo indisponibile, nessun successo falso | ____________________ |
| T14 | Tastiera, zoom 200%, testo grande, TalkBack, schermo piccolo | Suite browser e semantica foto riuscite; Kevin preferisce non attivare TalkBack: prova fisica aperta |
| T15 · P0 | Generale singolo/multipli dalla dashboard, inbox/banner/tocco, tre stati app | Singolo Samsung: tre stati riusciti; multipli: due account sequenziali, retry senza duplicazione riuscito. Configurazione contemporanea e verifica distribuita residue. [Evidenza](SAMSUNG-QA-2026-10-14.md) |
| T16 · P0 | Incarico dalla dashboard, disponibilità/push/deep link, nessun duplicato | Tre stati riusciti nel percorso candidato 14/10; tre task e tre notifiche senza duplicati; verifica distribuita residua. [Evidenza](SAMSUNG-QA-2026-10-14.md) |

Eccezione dispositivi: un Samsung fisico disponibile; due account provati sequenzialmente sul medesimo telefono. L'emulatore locale non parte senza accelerazione; recovery su emulatore Android riuscito in CI. Una ricezione simultanea su due dispositivi non è stata eseguita. Per Android l'arresto forzato dalle impostazioni richiede la riapertura dell'app per riprendere FCM; distinguere questa condizione dalla normale terminazione del processo.

## Gestione incidenti

- Critico: accesso tra aziende, perdita/duplicazione dati, backup non recuperabile o flusso principale bloccato. Sospendere pubblicazione e azioni interessate, conservare ID e audit, avvisare il referente nominato; ripristino prima in ambiente isolato.
- Alto: push assente/parziale, sessione/recovery o prestazioni che ostacolano il lavoro. Verificare inbox e incarico persistenti, stato provider e permesso Android; usare il retry dello stesso ID. Non reinviare un report con nuova chiave.
- Ordinario: difetto visivo o funzione secondaria. Registrare versione, contesto e comportamento riproducibile.

I tempi di risposta sono campi contrattuali da concordare. Nei ticket inserire requestId, ora, versione e gravità; escludere credenziali, GPS/foto/documenti superflui.

## Decisione finale

Pilota iniziato il __________; terminato il __________. Operazioni osservate ____; sessioni ____; crash ____; duplicati ____; API p95 ____; costo ____; restore RPO/RTO ____.

P0 aperti: __________. P1 esclusi con impatto e motivazione approvati: __________. Limitazioni consegnate e accettate: __________.

Decisione: ☐ Go ☐ No-Go. Responsabile: __________; data: __________; evidenza: __________.

Pubblicare solo dopo chiusura dei P0 e decisione documentata. Conservare artefatti esatti e rollback. L'APK viene consegnato nell'area HTTPS autenticata esistente; le comunicazioni ai clienti sono eseguite dal referente, non automaticamente da questo pacchetto.

APK/AAB firmati e simboli sono pronti nella consegna privata; candidata non pubblicata. Il recupero dell’autorità richiede anche il [registro separato](IDENTITY-RECOVERY-2026-10-13.md): scegliere offsite/custodi e verificare MFA prima del Go.
