export const AD_COOLDOWN_KEY = 'allez:ad-hidden-until:v1'
export const AD_COOLDOWN_MS = 60 * 60 * 1000
const CHANGE_EVENT = 'allez:ad-cooldown-changed'
let memoryUntil = 0

function cooldownUntil() {
  try {
    const saved = Number(window.localStorage.getItem(AD_COOLDOWN_KEY))
    return Math.max(Number.isFinite(saved) && saved > 0 ? saved : 0, memoryUntil)
  } catch {
    return memoryUntil
  }
}

export function adsAreHidden() {
  return cooldownUntil() > Date.now()
}

export function recordAdClick() {
  memoryUntil = Date.now() + AD_COOLDOWN_MS
  try { window.localStorage.setItem(AD_COOLDOWN_KEY, String(memoryUntil)) } catch { /* Keep this tab usable when storage is blocked. */ }
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

export function subscribeAdCooldown(listener) {
  let timer
  const refresh = () => {
    window.clearTimeout(timer)
    listener()
    const remaining = cooldownUntil() - Date.now()
    if (remaining > 0) timer = window.setTimeout(refresh, Math.min(remaining, AD_COOLDOWN_MS))
  }
  const storageChanged = (event) => {
    if (event.key === AD_COOLDOWN_KEY || event.key === null) refresh()
  }
  window.addEventListener(CHANGE_EVENT, refresh)
  window.addEventListener('storage', storageChanged)
  document.addEventListener('visibilitychange', refresh)
  refresh()
  return () => {
    window.clearTimeout(timer)
    window.removeEventListener(CHANGE_EVENT, refresh)
    window.removeEventListener('storage', storageChanged)
    document.removeEventListener('visibilitychange', refresh)
  }
}
