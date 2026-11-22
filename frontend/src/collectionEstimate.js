import { apiUrl } from './api.js'

export async function estimateCollection(criteria, partTime, signal) {
  const { searchId: _searchId, visitorId: _visitorId, sessionId: _sessionId, ...filters } = criteria
  const response = await fetch(apiUrl(`/api/${partTime ? 'part-time' : 'job'}-search/estimate`), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
    body: JSON.stringify({ criteria: filters, searchId: _searchId, visitorId: _visitorId, sessionId: _sessionId }),
  })
  // Frontend and backend deploy separately. Keep existing searches usable during rollout.
  if (response.status === 404) return null
  if (!response.ok) throw new Error('검색 건수를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  const result = await response.json()
  if (!Number.isInteger(result.total) || result.total < 0 || !Number.isInteger(result.minMinutes)
    || !Number.isInteger(result.maxMinutes) || result.minMinutes < 1 || result.maxMinutes < result.minMinutes
    || typeof result.confirmationRequired !== 'boolean') throw new Error('검색 건수를 확인하지 못했습니다.')
  return result
}
