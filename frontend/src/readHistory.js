export const READ_HISTORY_KEY = 'jobfitax.read-postings.v1'

export function postingKey(item) {
  return `${item.source}:${item.postingId}`
}

export function readHistory(storage) {
  try {
    const parsed = JSON.parse(storage.getItem(READ_HISTORY_KEY) || '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.fromEntries(Object.entries(parsed).filter(([, value]) =>
      typeof value === 'string' && Number.isFinite(Date.parse(value))))
  } catch {
    return {}
  }
}

export function rememberPosting(storage, item, now = new Date()) {
  const history = readHistory(storage)
  const key = postingKey(item)
  // Keep the first confirmation date, including when the same posting is reopened.
  const next = { ...history, [key]: history[key] || now.toISOString() }
  storage.setItem(READ_HISTORY_KEY, JSON.stringify(next))
  return next
}

export function confirmationLabel(value) {
  const date = new Date(value)
  return `${date.getMonth() + 1}월 ${date.getDate()}일 확인`
}
