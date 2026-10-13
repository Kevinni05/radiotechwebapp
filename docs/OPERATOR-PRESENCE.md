# Presenza operatori e sessione persistente

Candidata del 13 ottobre 2026. [Registro del piano e gate](ENTERPRISE-PLAN-2026-10-13.md). Presenza, disponibilità operativa e autorizzazione sono segnali distinti.

## Configurazione esistente

Istanza confermata ACTIVE: `https://gestionale-radio-default-rtdb.europe-west1.firebasedatabase.app`, regione europe-west1. Non occorre creare un database. Il 13 ottobre `FIREBASE_DATABASE_URL` è stato aggiunto al backend Cloud Run radiotech-web, al quale Render inoltra le richieste; `/api/v1/auth/public-config` espone l'URL corretto. Revisione pronta radiotech-web-00004-5sv.

Pubblicare database.rules.json solo sul progetto/istanza esistenti. Le regole consentono scritture al proprietario UID/tenant e letture ai ruoli autorizzati dell'azienda; validano stato, timestamp server e campi ammessi. I booleani legacy restano compatibili, senza attestare un'app aperta di recente.

## Protocollo e significato

Percorso: `presence/{tenantId}/{firebaseUid}/connections/{sessionId}`. Ogni processo usa una connessione indipendente. Registrare onDisconnect prima di pubblicare; cleanup rimuove solo la propria connessione e aggiorna lastSeen con tempo server.

- Resumed: `{state: 'foreground', updatedAt: ServerValue.timestamp}`, badge App aperta.
- Hidden/paused: background, badge In background. Inactive può essere camera/scanner/finestra di sistema.
- Detached/logout/revoca: cleanup della propria connessione. Crash/rete persa sono rilevati dal server e dalla soglia di freschezza, senza offline istantaneo garantito.
- Heartbeat 30 secondi su RTDB; freschezza 90 secondi con offset dell'orologio server e aggiornamento visivo ogni cinque secondi mentre Operatori è visibile.
- Foreground recente prevale sul background di un altro dispositivo. Timestamp futuri/vecchi, booleani legacy e lettore scollegato mostrano Stato non aggiornato; connessioni vuote con lastSeen mostrano Offline.

Cambio pagina, tab nascosta e logout rimuovono listener e timer. Disponibile/occupato/fuori turno restano indicatori operativi separati. I selettori di ruolo in modifica non vengono ricreati dai soli aggiornamenti presenza.

Il payload contiene solo stato e timestamp: nessun GPS, nome, email, task o contenuto delle notifiche. Nessun heartbeat su Firestore o Cloud Function. Misurare consumi RTDB nel pilota.

## Sessione ed evidenze

La sessione Firebase persiste. L'access gate verifica auth/me su avvio/resume/rete e ogni cinque minuti in foreground; sospensione/revoca impediscono di riattivare la presenza. Fallback locale cifrato massimo otto ore, senza aggirare l'autorizzazione delle API.

Tre test di regole RTDB, tre del resolver e i test mobili di ordine onDisconnect/pubblicazione, background, revoca e cambio account sono riusciti. T08 resta da verificare su Samsung più emulatore, variante concordata perché è disponibile un solo telefono fisico. L'emulatore non certifica FCM/GPS/camera di un secondo telefono. La candidata deve essere distribuita: pubblicare codice o URL non aggiorna l'APK.
