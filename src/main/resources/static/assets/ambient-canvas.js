/* Dependency-free ambient renderer shared by the console and customer portal. */
(() => {
  'use strict';
  if (document.querySelector('.ambient-field')) return;

  const root = document.documentElement;
  const field = document.createElement('div');
  field.className = 'ambient-field';
  field.setAttribute('aria-hidden', 'true');
  const canvas = document.createElement('canvas');
  field.append(canvas);
  const ctx = canvas.getContext('2d', { alpha: true });
  if (!ctx) return; // CSS fallback remains visible.

  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const forced = matchMedia('(forced-colors: active)');
  const finePointer = matchMedia('(hover: hover) and (pointer: fine)');
  let width = 0, height = 0, scale = 1, quality = 1;
  let frame = 0, lastTime = 0, elapsed = 0, suspended = false, printing = false;
  let resizePending = true, cost = 0, samples = 0;
  let targetX = 0, targetY = 0, pointerX = 0, pointerY = 0;
  const seed = Math.floor(Math.random() * 0x7fffffff);

  // Non-periodic gradient noise with a quintic fade: velocity and acceleration
  // remain continuous at lattice boundaries. No repeating keyframe timeline.
  function hash(n) {
    n = Math.imul(n ^ seed, 0x45d9f3b);
    n = Math.imul(n ^ (n >>> 16), 0x45d9f3b);
    return ((n ^ (n >>> 16)) >>> 0) / 4294967295;
  }
  function noise(t, channel) {
    const i = Math.floor(t), f = t - i;
    const fade = f * f * f * (f * (f * 6 - 15) + 10);
    const a = (hash(i + channel * 7919) * 2 - 1) * f;
    const b = (hash(i + 1 + channel * 7919) * 2 - 1) * (f - 1);
    return 2 * (a + (b - a) * fade);
  }
  function drift(t, channel) {
    return noise(t, channel) * .72 + noise(t * 2.03 + 13.7, channel + 101) * .28;
  }

  // Gradients are rasterized once, never regenerated or blurred per frame.
  // Soft shoulders deliberately avoid hard discs and bright particle outlines.
  function sprite(rgb, mist) {
    const image = document.createElement('canvas');
    image.width = image.height = 192;
    const g = image.getContext('2d');
    if (!g) return null;
    const gradient = g.createRadialGradient(96, 96, 0, 96, 96, 96);
    const stops = mist ? [[0,.42],[.28,.32],[.58,.12],[.82,.025],[1,0]]
      : [[0,.48],[.18,.43],[.4,.26],[.65,.075],[.85,.012],[1,0]];
    for (const [position, alpha] of stops) gradient.addColorStop(position, `rgba(${rgb},${alpha})`);
    g.fillStyle = gradient;
    g.fillRect(0, 0, 192, 192);
    return image;
  }
  const colors = ['154,43,58', '108,30,51', '172,67,39'];
  const glows = colors.map(color => sprite(color, false));
  const mists = colors.map(color => sprite(color, true));
  if ([...glows, ...mists].some(image => !image)) return;
  const particles = Array.from({ length: 24 }, (_, i) => {
    const depth = Math.floor(i / 8);
    return { channel: i + 1, depth, x: hash(i * 7 + 31), y: hash(i * 11 + 59),
      radius: [54, 90, 145][depth] * (.7 + hash(i + 93) * .65),
      opacity: [.16, .22, .19][depth], speed: .019 + hash(i + 117) * .018,
      image: glows[i % 9 === 0 ? 2 : i % 2] };
  });
  document.body.prepend(field);
  document.body.classList.add('ambient-ready');

  const lowMotion = () => reduced.matches || root.dataset.motion === 'reduced';
  const hidden = () => document.hidden || suspended || printing || forced.matches || root.dataset.contrast === 'high';

  function resize() {
    width = Math.max(1, window.innerWidth);
    height = Math.max(1, window.innerHeight);
    // Blurred lights do not benefit from retina resolution. Bound memory and
    // fill rate on 4K screens, with adaptive headroom on slower devices.
    scale = Math.min(window.devicePixelRatio || 1, 1, Math.sqrt(1200000 / (width * height))) * quality;
    canvas.width = Math.max(1, Math.round(width * scale));
    canvas.height = Math.max(1, Math.round(height * scale));
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
    resizePending = false;
  }
  function drawLight(image, x, y, radius, alpha, stretch = 1) {
    ctx.globalAlpha = alpha;
    ctx.drawImage(image, x - radius * stretch, y - radius, radius * 2 * stretch, radius * 2);
  }
  function render(dt) {
    if (resizePending) resize();
    const ease = 1 - Math.exp(-dt / .85);
    pointerX += (targetX - pointerX) * ease;
    pointerY += (targetY - pointerY) * ease;
    ctx.clearRect(0, 0, width, height);
    const size = Math.min(width, height);
    // Broad light grading, followed by three bokeh planes, back to front.
    for (let i = 0; i < 3; i++) {
      const t = elapsed * .011 + i * 17.3;
      const x = width * ([.12,.83,.42][i] + drift(t, i + 401) * .16) + pointerX * 3;
      const y = height * ([.24,.56,.93][i] + drift(t, i + 501) * .14) + pointerY * 3;
      drawLight(mists[i], x, y, size * .76, .65, 1.55);
    }
    for (const p of particles) {
      const t = elapsed * p.speed + p.channel * 9.37;
      const x = width * (p.x + drift(t, p.channel) * .14) + pointerX * (3 + p.depth * 3);
      const y = height * (p.y + drift(t + 23.4, p.channel + 51) * .12) + pointerY * (3 + p.depth * 3);
      // Fade before crossing the viewport edge; no wraparound or respawn jumps.
      const edge = Math.max(0, Math.min(1, x / (width * .12), (width - x) / (width * .12), y / (height * .12), (height - y) / (height * .12)));
      const fade = edge * edge * (3 - 2 * edge);
      const pulse = .78 + drift(t * .71 + 7.8, p.channel + 201) * .3;
      const radius = p.radius * Math.max(.6, Math.min(1.4, size / 900)) * (1 + drift(t * .6, p.channel + 301) * .08);
      drawLight(p.image, x, y, radius, p.opacity * pulse * fade);
    }
    ctx.globalAlpha = 1;
  }
  function stop() {
    cancelAnimationFrame(frame);
    frame = 0;
    lastTime = 0;
  }
  function tick(now) {
    frame = 0;
    if (hidden() || lowMotion()) return;
    const delta = lastTime ? now - lastTime : 1000 / 60;
    // Limit work on high-refresh displays without accumulating time drift.
    if (delta >= 1000 / 60 - 1) {
      const dt = Math.min(delta / 1000, .05);
      lastTime = now;
      elapsed += dt;
      const started = performance.now();
      render(dt);
      cost += performance.now() - started;
      if (++samples >= 180) {
        if (cost / samples > 5 && quality > .55) {
          quality = Math.max(.55, quality * .85);
          resizePending = true;
        }
        cost = samples = 0;
      }
    }
    frame = requestAnimationFrame(tick);
  }
  function sync() {
    stop();
    if (hidden()) return;
    if (lowMotion()) {
      targetX = targetY = pointerX = pointerY = 0;
      render(0); // One still image; no active rAF or timers.
    } else frame = requestAnimationFrame(tick);
  }
  window.addEventListener('pointermove', event => {
    if (!finePointer.matches || event.pointerType === 'touch' || hidden() || lowMotion()) return;
    targetX = event.clientX / width * 2 - 1;
    targetY = event.clientY / height * 2 - 1;
  }, { passive: true });
  const resetPointer = () => { targetX = targetY = 0; };
  document.documentElement.addEventListener('pointerleave', resetPointer, { passive: true });
  window.addEventListener('blur', resetPointer);
  window.addEventListener('resize', () => { resizePending = true; if (lowMotion() && !hidden()) sync(); }, { passive: true });
  document.addEventListener('visibilitychange', sync);
  reduced.addEventListener('change', sync);
  forced.addEventListener('change', sync);
  finePointer.addEventListener('change', resetPointer);
  new MutationObserver(sync).observe(root, { attributes: true, attributeFilter: ['data-motion', 'data-contrast', 'data-appearance'] });
  window.addEventListener('pagehide', () => { suspended = true; stop(); });
  window.addEventListener('pageshow', () => { suspended = false; resizePending = true; sync(); });
  window.addEventListener('beforeprint', () => { printing = true; stop(); });
  window.addEventListener('afterprint', () => { printing = false; sync(); });
  sync();
})();
