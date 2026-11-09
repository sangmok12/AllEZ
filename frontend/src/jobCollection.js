import { apiUrl } from './api.js'
import { readCollectionStream } from './collectionStream.js'

export async function collectJobs(criteria, onUpdate, signal) {
  const { searchId, visitorId, sessionId, ...filters } = criteria
  const response = await fetch(apiUrl('/api/job-search/collect'), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
    body: JSON.stringify({ criteria: filters, searchId, visitorId, sessionId }),
  })
  return readCollectionStream(response, onUpdate, { limit: 2000, sources: ['JOBKOREA', 'SARAMIN'] })
}
