export const SEARCH_AD_COUNT_KEY = 'allez:daily-search-count:v1'
export const FREE_SEARCH_COUNT = 3
const dayFormat = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' })
const memory = {}

// Shared across jobs/part-time searches and reloads. Count only validated submissions.
export function searchAdIsDue(storage, fallback = memory, now = Date.now()) {
  const day = dayFormat.format(new Date(now))
  let saved
  try { saved = JSON.parse(storage?.getItem(SEARCH_AD_COUNT_KEY) || 'null') } catch { /* Use this tab's count. */ }
  const validCount = value => value?.day === day && Number.isInteger(value.count) && value.count >= 0
    ? Math.min(value.count, FREE_SEARCH_COUNT + 1) : 0
  const count = Math.min(Math.max(validCount(saved), validCount(fallback)) + 1, FREE_SEARCH_COUNT + 1)
  Object.assign(fallback, { day, count })
  try { storage?.setItem(SEARCH_AD_COUNT_KEY, JSON.stringify({ day, count })) } catch { /* Searches still work without storage. */ }
  return count > FREE_SEARCH_COUNT
}

export function recordSearchForAds() {
  let storage
  try { storage = window.localStorage } catch { /* Storage access itself can be blocked. */ }
  return searchAdIsDue(storage)
}
