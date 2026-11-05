import { apiUrl } from './api'
import { getAnalyticsSession } from './analyticsSession'

const memory = {}
const announced = new Set()
const categories = new Set(['jobs', 'part_time', 'homes'])
let lastTouch = 0

function identity() {
  let storage
  try { storage = window.localStorage } catch { storage = { getItem: () => null, setItem: () => {} } }
  return getAnalyticsSession(storage, memory)
}

function deliver(event, attempt = 0) {
  // Never await analytics in a user action. Reuse the event ID when retrying.
  void fetch(apiUrl('/api/usage-events'), {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(event), keepalive: true,
    signal: AbortSignal.timeout(150000),
  }).then((response) => {
    if (!response.ok && (response.status >= 500 || response.status === 429)) throw new Error('retry')
  }).catch(() => {
    if (attempt < 2) setTimeout(() => deliver(event, attempt + 1), 5000 * (attempt + 1))
  })
}

function emit(category, eventType, context, details = {}) {
  deliver({ eventId: crypto.randomUUID(), occurredAt: new Date().toISOString(),
    category, eventType, ...context, ...details })
}

export function analyticsContext(category = 'jobs') {
  if (!categories.has(category)) throw new Error('Unknown analytics category')
  const context = identity()
  const key = `${context.sessionId}:${category}`
  if (!announced.has(key)) {
    announced.add(key)
    emit(category, 'page_view', context)
  }
  return context
}

export function startUsageTracking(category = 'jobs') {
  analyticsContext(category)
  const activity = () => {
    if (Date.now() - lastTouch < 10000) return
    lastTouch = Date.now()
    analyticsContext(category)
  }
  window.addEventListener('pointerdown', activity, { passive: true })
  window.addEventListener('keydown', activity)
  return () => {
    window.removeEventListener('pointerdown', activity)
    window.removeEventListener('keydown', activity)
  }
}

export function trackSearch(criteria, category = 'jobs') {
  const context = analyticsContext(category)
  emit(category, 'search_click', context, {
    searchId: criteria.searchId,
    criteria: { keyword: criteria.keyword, regionIds: criteria.regionIds,
      careerType: criteria.careerType, minimumCareerYears: criteria.minimumCareerYears,
      maximumCareerYears: criteria.maximumCareerYears, minimumEducation: criteria.minimumEducation,
      employmentTypes: criteria.employmentTypes },
  })
  return context
}

export function trackPosting(item, searchId, category = 'jobs') {
  emit(category, 'posting_click', analyticsContext(category),
    { searchId, source: item.source, postingId: String(item.postingId) })
}
