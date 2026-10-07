# Deploy gratuito su Render

Questa configurazione pubblica RADIO TECH come **Render Web Service** usando il `Dockerfile` del repository.

## Sicurezza e prerequisiti

- Non committare mai il JSON del service account Firebase.
- In Render crea un **Secret File** chiamato esattamente `firebase-service-account.json`.
- Il file sarà disponibile a runtime come `/etc/secrets/firebase-service-account.json`.
- Il `Dockerfile` abilita l'utente non-root dell'app a leggere i secret file Render.
- `RADIOTECH_AI_ENABLED=false`: Ollama non è adatto al piano free.

## Creazione del servizio

1. In Render scegli **New > Blueprint**.
2. Collega il repository GitHub `Kevinni05/radiotechwebapp`.
3. Render rileverà `render.yaml`.
4. Inserisci i valori richiesti per le variabili con `sync: false`:
   - `RADIOTECH_FIREBASE_WEB_API_KEY`
   - `RADIOTECH_CAPO_EMAIL`
   - `RADIOTECH_PUBLIC_BASE_URL`
   - `RADIOTECH_CORS_ORIGINS`
5. Per URL pubblico e CORS usa l'origine HTTPS assegnata da Render, per esempio:
   `https://radiotechwebapp.onrender.com`
6. Nel servizio Render apri **Environment > Secret Files** e crea:
   - Filename: `firebase-service-account.json`
   - Contents: il JSON del service account Firebase usato dal backend.
7. Salva e avvia/redeploya il servizio.

## Pipeline

`autoDeployTrigger: checksPass` impedisce a Render di distribuire un commit su `main` finché i check CI collegati non risultano superati.

La CI esistente verifica backend, regole Firebase, integrazione con emulatori, browser workflow, artefatto riproducibile e build Docker.

## Health check

Render usa:

```
/actuator/health/readiness
```

La readiness include anche Firestore e la configurazione di verifica report. Un deploy con dipendenze non sane non viene promosso.

## File system

Il piano Render Free usa filesystem effimero. Non salvare allegati o dati persistenti nel disco locale del container. Usa Firebase Storage / Firestore per i dati persistenti.

## Limite del piano free

Il servizio può andare in sleep dopo un periodo di inattività. Il primo accesso successivo può quindi richiedere un cold start.
