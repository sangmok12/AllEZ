import { apiUrl } from './api.js'

export async function readCollectionStream(response, onUpdate) {
  if (!response.ok || !response.body) throw new Error('검색 서버에 연결하지 못했습니다. 다시 시도해 주세요.')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = '', completed = null
  const consume = (line) => {
    if (!line.trim()) return
    const update = JSON.parse(line)
    if (update.phase === 'error' || update.phase === 'rejected') throw new Error(update.message)
    if (!['checking', 'collecting', 'sorting', 'complete'].includes(update.phase)) throw new Error('검색 응답을 확인하지 못했습니다.')
    onUpdate(update)
    if (update.phase === 'complete') {
      if (!Array.isArray(update.result?.items) || !Array.isArray(update.result?.sites)
        || update.result.items.length > 1000 || update.result.items.length !== update.total
        || update.result.sites.length !== 2 || update.result.sites.some(site => site.status !== 'SUCCESS' || site.nextPage != null)) {
        throw new Error('전체 검색 결과를 확인하지 못했습니다. 다시 검색해 주세요.')
      }
      completed = update.result
    }
  }
  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const lines = buffer.split('\n')
      buffer = lines.pop()
      lines.forEach(consume)
      if (done) { consume(buffer); break }
    }
    if (!completed) throw new Error('수집이 완료되기 전에 연결이 끊겼습니다. 다시 검색해 주세요.')
    return completed
  } catch (error) {
    if (error instanceof SyntaxError) throw new Error('검색 응답을 확인하지 못했습니다. 다시 검색해 주세요.', { cause: error })
    throw error
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}

export async function collectPartTime(criteria, onUpdate, signal) {
  const { searchId, visitorId, sessionId, keyword, regionIds, neighborhoodId, neighborhoodName } = criteria
  const response = await fetch(apiUrl('/api/part-time-search/collect'), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
    body: JSON.stringify({ criteria: { keyword, regionIds, neighborhoodId, neighborhoodName }, searchId, visitorId, sessionId }),
  })
  return readCollectionStream(response, onUpdate)
}
