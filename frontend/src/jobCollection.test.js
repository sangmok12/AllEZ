import test from 'node:test'
import assert from 'node:assert/strict'
import { readCollectionStream } from './collectionStream.js'

const options = { sources: ['JOBKOREA', 'SARAMIN'] }
function complete(count, sources = options.sources) {
  const result = { items: Array.from({ length: count }, (_, id) => ({ postingId: String(id) })), sites: sources.map(source => ({ source, status: 'SUCCESS', nextPage: null })) }
  return { phase: 'complete', total: count, result }
}
function response(updates) {
  return new Response(updates.map(update => JSON.stringify(update)).join('\n') + '\n')
}
test('career collections accept broad results while rejecting incorrect source identities', async () => {
  assert.equal((await readCollectionStream(response([complete(2000)]), () => {}, options)).items.length, 2000)
  assert.equal((await readCollectionStream(response([complete(25001)]), () => {}, options)).items.length, 25001)
  await assert.rejects(readCollectionStream(response([complete(2, ['JOBKOREA', 'JOBKOREA'])]), () => {}, options), /전체 검색 결과/)
  await assert.rejects(readCollectionStream(response([complete(2, ['ALBAMON', 'ALBAHEAVEN'])]), () => {}, options), /전체 검색 결과/)
})
test('career progress is visible but failed or interrupted collections never become results', async () => {
  const progress = []
  await assert.rejects(readCollectionStream(response([
    { phase: 'collecting', collected: 100, total: 1500 }, { phase: 'error', message: '원본 오류' },
  ]), update => progress.push(update), options), /원본 오류/)
  assert.equal(progress[0].collected, 100)
  await assert.rejects(readCollectionStream(response([{ phase: 'sorting', collected: 100, total: 100 }]), () => {}, options), /연결이 끊겼/)
})
