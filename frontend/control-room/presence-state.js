export const PRESENCE_FRESHNESS_MS = 90_000;

// One frozen phone must neither keep an operator foreground forever nor hide
// a second, active phone. Availability remains a separate business field.
export function resolvePresence(record, {connected = true, now = Date.now(), freshnessMs = PRESENCE_FRESHNESS_MS} = {}) {
  const unknown = {state:'unknown',online:false,known:false,label:'Stato non aggiornato',lastSeen:null};
  if (!connected || !record || typeof record !== 'object') return unknown;
  const sessions = Object.values(record.connections || {});
  const fresh = sessions.filter(session => session && typeof session === 'object'
    && ['foreground','background'].includes(session.state)
    && Number.isFinite(session.updatedAt) && session.updatedAt <= now + 10_000
    && now - session.updatedAt <= freshnessMs);
  const timestamps = [record.lastSeen, ...sessions.map(session => session?.updatedAt)].filter(Number.isFinite);
  const lastSeen = timestamps.length ? Math.max(...timestamps) : null;
  const state = fresh.some(session => session.state === 'foreground') ? 'foreground'
    : fresh.length ? 'background' : sessions.length ? 'stale' : lastSeen ? 'offline' : 'unknown';
  return {state,known:state !== 'unknown',online:state === 'foreground',lastSeen,
    label:({foreground:'App aperta',background:'In background',offline:'Offline',stale:'Stato non aggiornato',unknown:'Stato non aggiornato'})[state]};
}
