export const SESSION_KEY = 'allez.analytics-session.v1'
export const SESSION_TIMEOUT = 30 * 60 * 1000
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

// Storage is shared by tabs; memory is a fallback when browser storage is blocked.
export function getAnalyticsSession(storage, memory, now = Date.now(), makeId = () => crypto.randomUUID()) {
  let saved = memory.value
  try { saved = JSON.parse(storage.getItem(SESSION_KEY)) || saved } catch { /* Use memory. */ }
  const visitorId = UUID.test(saved?.visitorId || '') ? saved.visitorId : makeId()
  const active = UUID.test(saved?.sessionId || '') && Number.isFinite(saved?.lastActivity)
    && now >= saved.lastActivity && now - saved.lastActivity < SESSION_TIMEOUT
  const value = { visitorId, sessionId: active ? saved.sessionId : makeId(), lastActivity: now }
  memory.value = value
  try { storage.setItem(SESSION_KEY, JSON.stringify(value)) } catch { /* Tracking still works for this page. */ }
  return { visitorId: value.visitorId, sessionId: value.sessionId }
}
