import { apiUrl } from './api.js'
import { readCollectionStream } from './collectionStream.js'
export { readCollectionStream } from './collectionStream.js'

export async function collectPartTime(criteria, onUpdate, signal) {
  const { searchId, visitorId, sessionId, keyword, regionIds, neighborhoodId, neighborhoodName, locations } = criteria
  const response = await fetch(apiUrl('/api/part-time-search/collect'), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
    body: JSON.stringify({ criteria: { keyword, regionIds, neighborhoodId, neighborhoodName, locations }, searchId, visitorId, sessionId }),
  })
  return readCollectionStream(response, onUpdate)
}
