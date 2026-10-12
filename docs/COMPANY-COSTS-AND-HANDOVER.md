# Proposta di consegna aziendale

Aggiornamento: 12 ottobre 2026. Il progetto corrente rimane un ambiente di test: non vengono attivati abbonamenti, billing, bucket o Functions a pagamento.

## Configurazione consigliata

Per il primo cliente consiglio una licenza d'uso annuale non esclusiva, con manutenzione/supporto separati, conservando la proprietà del software. Firebase, Render, dominio e pagamenti devono essere intestati all'azienda. Un ambiente dedicato per cliente facilita separazione dei dati, fatturazione e uscita. L'azienda dispone di almeno due amministratori propri; il fornitore riceve un accesso nominativo limitato per assistenza. Cessione dei sorgenti e sviluppo esclusivo sono un'offerta distinta, da definire nel contratto.

## Costi dell'infrastruttura

Prezzi Render verificati sulla [pagina ufficiale](https://render.com/pricing): workspace Pro 25 USD/mese; compute 1c-2g (1 CPU, 2 GB) 25 USD/mese; compute 2c-4g (2 CPU, 4 GB) 85 USD/mese. Compute e workspace si sommano. [Regole del workspace](https://render.com/blog/better-pricing-for-fast-growing-teams): Pro include 25 GB di banda, extra 0,15 USD/GB. Prezzi prima di imposte e conversione valuta.

| Scenario | Base Render | Riserva Firebase/storage/backup | Dominio e altri servizi | Budget infrastruttura proposto |
|---|---:|---:|---:|---:|
| Piccola azienda, una istanza 1c-2g | 50 USD/mese | 10–25 USD/mese | 2–10 USD/mese | 65–90 USD/mese |
| Riferimento: 50 operatori, 1.000 asset, due istanze 1c-2g | 75 USD/mese | 20–50 USD/mese | 5–15 USD/mese | 100–150 USD/mese |
| Maggiore capacità: due istanze 2c-4g | 195 USD/mese | 50–120 USD/mese | 5–25 USD/mese | 250–350 USD/mese |

Le riserve sono ipotesi prudenziali di budget, non fatture o prezzi garantiti. Non includono licenza del software, assistenza umana, IVA, servizi di identità a pagamento, traffico eccezionale o un SLA acquistato. La replica richiede configurazione coerente dei limiti/gateway e prove di carico prima di essere proposta come capacità garantita. "Massimo" non significa attivare il piano più costoso: aumentare risorse dopo misure p95/p99, memoria e carico.

[Firebase](https://firebase.google.com/pricing) fattura Blaze a consumo. Firestore Standard include una quota gratuita giornaliera; foto, download e retention dominano la crescita dei byte. [Cloud Storage richiede Blaze dal 3 febbraio 2026](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024). Per l'azienda configurare la regione UE, il bucket, backup separati, budget/alert al 50/80/100% e destinatari nominativi; gli alert non sono un limite automatico alla spesa. Nel test restano attivi gli adapter Firestore e i test locali/emulatori. Nessuna tariffa viene attivata da questo documento.

Ipotesi mensili per i tre scenari: 200/1.000/5.000 report, 3/5/5 foto per report, 500 KB per foto = 0,3/2,5/12,5 GB nuovi al mese. Con tre mesi di conservazione delle foto il solo stock foto è circa 0,9/7,5/37,5 GB; aggiungere PDF, firme, copie di backup, download e APK. La retention dei report può essere diversa da quella delle foto e va approvata.

## Conservazione e privacy: proposta da approvare

Raccogliere GPS solo per gli eventi operativi necessari; evitare tracciamento continuo o fuori turno. Proposta iniziale: telemetria separata 30 giorni, foto non documentali 90 giorni, audit operativo 12 mesi, backup a rotazione 30 giorni. Report, allegati documentali e firme seguono la durata contrattuale e gli obblighi individuati dall'azienda: non rimuovere singoli campi dal documento firmato/hashato invalidandone l'integrità. Blocchi di conservazione per contestazioni e obblighi applicabili prevalgono sulla pulizia automatica.

Questi periodi sono proposte operative, non tempi imposti dalla legge né una policy già attivata. L'azienda deve approvare finalità, base giuridica, informative, diritti, destinatari, DPA e conservazione prima dell'uso con lavoratori. Il [GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj) richiede minimizzazione e limitazione della conservazione. Il [Garante](https://www.garanteprivacy.it/web/guest/home/docweb/-/docweb-display/docweb/10293888) richiama anche le condizioni dell'art. 4 dello Statuto dei lavoratori per i sistemi GPS: permesso Android e consenso nell'app non sostituiscono questa verifica.

## Responsabilità e handover

| Responsabilità | Test attuale | Consegna al cliente |
|---|---|---|
| Account e pagamenti | Proprietario RadioTech | Due amministratori aziendali nominati |
| Hosting/Firebase/backup | Proprietario RadioTech | Referente IT aziendale e sostituto |
| Firma Android e simboli | Custode della firma esistente | Custode nominato; copia cifrata e prova di recupero |
| Supporto e incidenti | Proprietario RadioTech | Fornitore con contratto, canale ed escalation |
| Privacy e retention | Nessuna policy commerciale attivata | Azienda titolare e referente privacy/DPO, se previsto |

Prima della consegna registrare nominativi reali, account, dominio, costi approvati, certificato, digest APK, versionCode, revisioni, backup/restore e RPO/RTO misurati. Provare accesso e recupero senza l'account personale dello sviluppatore. Non inserire password o chiavi in verbali, repository o ticket. La firma grafica non viene presentata come firma qualificata.

## Pilota e accettazione

Il pilota di due settimane, o un campione equivalente esplicitamente concordato, non può essere sostituito automaticamente dalla CI. Registrare operazioni, dispositivi, crash, duplicati, tempi, rete, feedback e incidenti. Il report di verifica deve separare prove su emulatori, prove sul telefono QA, API/Firebase reali e approvazioni commerciali. Fino all'approvazione del cliente i gate di vendita rimangono aperti.
