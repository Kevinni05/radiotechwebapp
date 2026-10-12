import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('../src/main/resources/static/assets/ambient-canvas.js', import.meta.url), 'utf8');

// Deterministic animation clock: prove that lifecycle transitions actually stop
// rendering, rather than merely hiding a canvas that continues consuming power.
function harness({ reduced = false, supported = true, hidden = false } = {}) {
  const events = target => Object.assign(target, {
    listeners: new Map(),
    addEventListener(name, callback) {
      const list = this.listeners.get(name) || [];
      this.listeners.set(name, [...list, callback]);
    },
    emit(name, event = {}) { for (const callback of this.listeners.get(name) || []) callback(event); },
  });
  const frames = new Map(), media = new Map(), draws = [];
  let id = 0, observer, clears = 0, field;
  const root = events({ dataset: {} });
  const classes = new Set();
  const context = { setTransform() {}, clearRect() { clears++; },
    createRadialGradient: () => ({ addColorStop() {} }), fillRect() {},
    drawImage(...args) { draws.push(args.slice(1)); } };
  const document = events({ hidden, documentElement: root,
    querySelector: () => field,
    createElement: tag => ({ tag, setAttribute() {}, append(child) { this.child = child; },
      getContext: () => supported ? context : null }),
    body: { prepend(element) { field = element; }, classList: { add: name => classes.add(name) } },
  });
  const window = events({ innerWidth: 1440, innerHeight: 900, devicePixelRatio: 2 });
  const sandbox = { document, window, Math, performance: { now: () => 0 },
    matchMedia(query) {
      const result = events({ matches: query.includes('reduced-motion') ? reduced : query.includes('pointer: fine') });
      media.set(query, result); return result;
    },
    requestAnimationFrame(callback) { const key = ++id; frames.set(key, callback); return key; },
    cancelAnimationFrame(key) { frames.delete(key); },
    MutationObserver: class { constructor(callback) { observer = callback; } observe() {} },
  };
  runInNewContext(source, sandbox);
  return { window, document, root, frames, draws, classes, media,
    get clears() { return clears; }, get canvas() { return field?.child; },
    step(now) { const pending = [...frames.values()]; frames.clear(); pending.forEach(callback => callback(now)); },
    attribute(name, value) { root.dataset[name] = value; observer(); },
    rerun() { runInNewContext(source, sandbox); },
  };
}

test('hidden tab cancels rendering and resumes without a time jump or duplicate loops', () => {
  const h = harness(); h.step(1000);
  assert.equal(h.frames.size, 1); const count = h.clears;
  h.document.hidden = true; h.document.emit('visibilitychange'); h.step(60000);
  assert.equal(h.frames.size, 0); assert.equal(h.clears, count);
  h.document.hidden = false; h.document.emit('visibilitychange'); h.document.emit('visibilitychange');
  assert.equal(h.frames.size, 1); h.step(60016); assert.equal(h.clears, count + 1);
});
test('reduced motion paints once, ignores the pointer, redraws on resize and can resume', () => {
  const h = harness({ reduced: true });
  assert.equal(h.frames.size, 0); assert.equal(h.clears, 1);
  h.window.emit('pointermove', { clientX: 1400, clientY: 850, pointerType: 'mouse' });
  h.window.innerWidth = 390; h.window.emit('resize');
  assert.equal(h.clears, 2); assert.equal(h.frames.size, 0); assert.equal(h.canvas.width, 390);
  const media = h.media.get('(prefers-reduced-motion: reduce)'); media.matches = false; media.emit('change');
  assert.equal(h.frames.size, 1);
  h.attribute('motion', 'reduced'); assert.equal(h.frames.size, 0);
});
test('high contrast, forced colors, printing and page cache suspend the render loop', () => {
  const h = harness(); h.step(1000);
  h.attribute('contrast', 'high'); assert.equal(h.frames.size, 0);
  h.attribute('contrast', 'normal'); assert.equal(h.frames.size, 1);
  const forced = h.media.get('(forced-colors: active)');
  forced.matches = true; forced.emit('change'); assert.equal(h.frames.size, 0);
  forced.matches = false; forced.emit('change'); assert.equal(h.frames.size, 1);
  for (const [stop, resume] of [['beforeprint', 'afterprint'], ['pagehide', 'pageshow']]) {
    h.window.emit(stop); assert.equal(h.frames.size, 0);
    h.window.emit(resume); assert.equal(h.frames.size, 1);
  }
});
test('4K/retina rendering stays bounded and every light has finite geometry', () => {
  const h = harness(); h.window.innerWidth = 3840; h.window.innerHeight = 2160;
  h.window.emit('resize'); h.step(1000);
  assert.ok(h.canvas.width * h.canvas.height < 1203000);
  for (let i = 1; i <= 600; i++) h.step(1000 + i * 1000 / 60);
  assert.ok(h.draws.length > 1000);
  assert.ok(h.draws.every(draw => draw.every(Number.isFinite)));
  assert.equal(h.frames.size, 1);
});
test('fallback survives missing canvas support and initialization is idempotent', () => {
  const unsupported = harness({ supported: false });
  assert.equal(unsupported.frames.size, 0); assert.equal(unsupported.canvas, undefined);
  assert.equal(unsupported.classes.has('ambient-ready'), false);
  const h = harness(); const canvas = h.canvas; h.rerun();
  assert.equal(h.canvas, canvas); assert.equal(h.frames.size, 1);
  const initiallyHidden = harness({ hidden: true });
  assert.equal(initiallyHidden.clears, 0); assert.equal(initiallyHidden.frames.size, 0);
});
test('both pages include same-origin renderer and styles; obsolete grid and loop are disabled', () => {
  for (const file of ['control-room.html', 'customer-portal.html']) {
    const html = readFileSync(new URL(`../src/main/resources/templates/${file}`, import.meta.url), 'utf8');
    assert.equal((html.match(/src="\/assets\/ambient-canvas.js" defer/g) || []).length, 1);
    assert.match(html, /href="\/assets\/ambient-canvas.css"/);
  }
  const css = readFileSync(new URL('../src/main/resources/static/assets/ambient-canvas.css', import.meta.url), 'utf8');
  assert.match(css, /body::after\s*\{ content: none;/);
  assert.match(css, /animation: none/);
  assert.match(css, /pointer-events: none/);
});
