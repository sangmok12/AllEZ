const TOKEN_KEY = 'allez.admin-session'
const USERNAME_KEY = 'allez.admin-username'
const KEEP_KEY = 'allez.admin-keep-login'

function local() { return window.localStorage }
function tab() { return window.sessionStorage }

export function readSession(storage, now = Date.now()) {
  try {
    const raw = storage.getItem(TOKEN_KEY)
    if (!raw) return ''
    // Older tab-only sessions are validated by the server on first use.
    if (!raw.startsWith('{')) return raw
    const saved = JSON.parse(raw)
    if (typeof saved.token === 'string' && Number.isFinite(saved.expiresAt) && saved.expiresAt > now) return saved.token
    storage.removeItem(TOKEN_KEY)
  } catch { /* Invalid or unavailable storage. */ }
  return ''
}

export function savedToken() {
  try { const token = readSession(local()); if (token) return token } catch { /* Try this tab. */ }
  try { return readSession(tab()) } catch { return '' }
}

export function saveToken(value, persistent = false, expiresIn = 28800) {
  try { local().removeItem(TOKEN_KEY) } catch { /* Browser may disable storage. */ }
  try { tab().removeItem(TOKEN_KEY) } catch { /* Browser may disable storage. */ }
  if (!value) return
  const encoded = JSON.stringify({ token: value, expiresAt: Date.now() + expiresIn * 1000 })
  try { (persistent ? local() : tab()).setItem(TOKEN_KEY, encoded) }
  catch { try { tab().setItem(TOKEN_KEY, encoded) } catch { /* Current page still works. */ } }
}

export function savedUsername() {
  try { return (local().getItem(USERNAME_KEY) || '').slice(0,100) } catch { return '' }
}
export function saveUsername(value) {
  try { if (value) local().setItem(USERNAME_KEY, value); else local().removeItem(USERNAME_KEY) } catch { /* Optional preference. */ }
}
export function keepLoginPreference() {
  try { return local().getItem(KEEP_KEY) === 'true' } catch { return false }
}
export function saveKeepLoginPreference(value) {
  try { local().setItem(KEEP_KEY, String(value)) } catch { /* Optional preference. */ }
}
