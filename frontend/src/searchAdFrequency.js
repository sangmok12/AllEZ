const SEARCH_COUNT_KEY = 'allez:search-ad-count:v1'

// Count valid, user-submitted searches across categories and browser reloads.
export function recordSearchForAd(storage) {
  try {
    const saved = Number(storage.getItem(SEARCH_COUNT_KEY))
    const count = Number.isInteger(saved) && saved >= 0 ? saved : 0
    if (count >= 4) return false
    storage.setItem(SEARCH_COUNT_KEY, String(count + 1))
    return count + 1 === 4
  } catch {
    // Unavailable browser storage must never prevent a search.
    return false
  }
}
