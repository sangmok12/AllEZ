import test from 'node:test'
import assert from 'node:assert/strict'
import { readCollectionStream } from './partTimeCollection.js'

function stream(events, chunkSize = 7) {
  const bytes = new TextEncoder().encode(events.map(JSON.stringify).join('\n') + '\n')
  return new Response(new ReadableStream({ start(controller) {
    for (let i = 0; i < bytes.length; i += chunkSize) controller.enqueue(bytes.slice(i, i + chunkSize))
    controller.close()
  } }))
}
test('reads split UTF-8 progress and exposes only a complete result', async () => {
  const result = { items: [{ postingId: '1' }], sites: [
    { source: 'ALBAMON', status: 'SUCCESS', nextPage: null },
    { source: 'ALBAHEAVEN', status: 'SUCCESS', nextPage: null },
  ] }
  const updates = []
  const response = stream([{ phase: 'collecting', collected: 1, total: 1, message: '수집 중' },
    { phase: 'sorting', total: 1 }, { phase: 'complete', total: 1, result }])
  assert.deepEqual(await readCollectionStream(response, u => updates.push(u)), result)
  assert.equal(updates[0].message, '수집 중')
})
test('rejects over-limit, failed, interrupted and malformed collections', async () => {
  await assert.rejects(readCollectionStream(stream([{ phase: 'rejected', total: 501, message: '범위를 좁혀 주세요' }]), () => {}), /범위를/)
  await assert.rejects(readCollectionStream(stream([{ phase: 'error', message: '원본 실패' }]), () => {}), /원본 실패/)
  await assert.rejects(readCollectionStream(stream([{ phase: 'collecting', total: 10, collected: 1 }]), () => {}), /연결이 끊겼/)
  await assert.rejects(readCollectionStream(stream([{ phase: 'complete', total: 10, result: { items: [], sites: [] } }]), () => {}), /전체 검색 결과/)
})
