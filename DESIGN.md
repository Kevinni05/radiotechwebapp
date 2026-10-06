# RadioTech — nuovo design della web app

Il restyling del 3 ottobre 2026 sostituisce il precedente foglio di stile integrato nella pagina con un sistema visivo esterno. Le funzionalità e gli identificatori dei controlli sono mantenuti: login, rete, operatori, incarichi, report, inventario, comunicazioni, incidenti, competenze, AI e squadra condividono la nuova impostazione.

## Direzione visiva

Blu notte e grafite, superfici sobrie, bordi sottili, accenti azzurri e indicatori semantici verde/ambra/rosa. La palette è definita nelle variabili di `src/main/resources/static/assets/design-system.css`. Manrope è ospitato localmente, con la [licenza originale SIL OFL](https://github.com/google/fonts/blob/main/ofl/manrope/OFL.txt) conservata in `assets/fonts/OFL-Manrope.txt`; il browser non deve contattare servizi di font esterni.

Il login presenta un campo radio astratto animato e un modulo dedicato. La console usa una navigazione laterale con area scorrevole e identità separata, un'introduzione alla dashboard, una fascia di indicatori reali e accessi rapidi ai workspace. Tabelle, moduli, schede, dialoghi, chat, messaggi di stato e viste mobili hanno regole comuni.

## Movimento e accessibilità

- Comparsa progressiva delle schede durante lo scroll, con `IntersectionObserver`, opacità e spostamento breve. Il contenuto resta visibile quando l'animazione è disattivata o non supportata.
- Transizioni fra viste, feedback sui pulsanti, barre di avanzamento e apertura dei dialoghi; il campo radio decorativo ruota lentamente nel login.
- `prefers-reduced-motion` disattiva movimento e scroll animato, anche quando la preferenza cambia durante l'utilizzo.
- Menu mobile con sfondo di separazione, pulsanti etichettati, `aria-expanded`, esclusione del contenuto sottostante dal focus, navigazione con Tab e chiusura con Escape.
- Dialoghi etichettati, focus iniziale sui campi, ciclo di Tab e restituzione del focus alla chiusura. La sezione corrente della navigazione usa `aria-current`.

Le animazioni sono presentazione: non ritardano le scritture, non generano numeri intermedi e non cambiano lo stato operativo. Le illustrazioni del login sono decorative; non rappresentano telemetria live.

## Logo finale

Il logo è stato creato **dopo il completamento e la verifica del restyling**, usando la skill `imagegen` e lo strumento integrato `image_gen`, senza CLI o chiavi API aggiuntive. Il simbolo combina una R geometrica con archi di segnale radio. Il nome RadioTech rimane testo HTML nella tipografia del prodotto.

File definitivo: `src/main/resources/static/assets/brand/radiotech-symbol-v1.png`, PNG RGBA trasparente, 1254 × 1254 px. L'originale generato è conservato nella directory Codex; una copia autonoma è inclusa nel progetto. Il simbolo è usato nel login desktop/mobile, nel menu laterale e come favicon. La resa è stata controllata su sfondo blu notte e a dimensione ridotta.

Prompt finale inviato allo strumento:

```text
Use case: logo-brand. Asset type: final production brand symbol for RadioTech, an enterprise radio infrastructure and workforce management web application. Create one original elegant symbol only, no wordmark, no text. A precise geometric monogram suggesting an R integrated with a subtle radio signal: a broad engineered vertical stroke, a clean rounded upper bowl formed by two restrained concentric signal arcs, and a confident diagonal lower leg. Strong single silhouette with open negative space, meticulous optical balance, distinctive yet understated, readable at 24px. Flat vector-like artwork with razor-sharp contours, no sketching. Palette: pale porcelain blue #C4DDFB as the dominant fill, with a restrained secondary slate blue #8CB5E7. Intended to sit on a deep navy #0B1019 UI, but the entire canvas/background must be genuinely transparent. Square composition, one centered mark occupying about 80% of the canvas, generous equal safe margins. No enclosing square, no background plate, no mockup, no lettering, no tiny decoration, no 3D, no shadows, no metallic effects, no gradients, no lens flare, no watermark. Produce the finished high-quality transparent logo asset.
```

## File e verifiche

`design-system.css` definisce aspetto e responsive; `design-system.js` gestisce icone SVG coerenti, intestazioni dei workspace, animazioni e focus. `control-room.html` contiene il nuovo layout. Nessuna libreria di animazione o risorsa remota aggiuntiva è necessaria. La CSP e l'autenticazione rimangono attive.

La suite Chromium comprende dodici percorsi: quelli applicativi esistenti più login e font locali, dashboard/accessi rapidi, movimento ridotto e focus/menu/dialoghi. Le API applicative sono simulate nei test browser; controllo HTTP non autenticato, template, risorse e CSP sono reali. Le immagini QA desktop/mobile sono in `build/reports/web/redesign-*.png`. Il riepilogo del rilascio e gli hash sono in [VERIFICATION.md](VERIFICATION.md).
