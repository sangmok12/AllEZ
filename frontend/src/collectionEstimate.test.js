import test from 'node:test'
import assert from 'node:assert/strict'
import { estimateCollection } from './collectionEstimate.js'

test('estimate carries the original search identity outside the filter object', async () => {
  const original = globalThis.fetch
  try {
    globalThis.fetch = async (url, options) => {
      assert.match(url, /part-time-search\/estimate$/)
      const body = JSON.parse(options.body)
      assert.equal(body.searchId, 'search-id')
      assert.equal(body.criteria.searchId, undefined)
      return Response.json({ total: 14000, minMinutes: 2, maxMinutes: 4, confirmationRequired: true })
    }
    assert.equal((await estimateCollection({ keyword: '', regionIds: ['B000'], searchId: 'search-id' }, true)).total, 14000)
  } finally { globalThis.fetch = original }
})

test('rollout compatibility accepts only a missing endpoint; real errors stay errors', async () => {
  const original = globalThis.fetch
  try {
    globalThis.fetch = async () => new Response('', { status: 404 })
    assert.equal(await estimateCollection({ keyword: 'java' }, false), null)
    globalThis.fetch = async () => new Response('', { status: 503 })
    await assert.rejects(estimateCollection({ keyword: 'java' }, false), /검색 건수/)
    globalThis.fetch = async () => Response.json({ total: 1, minMinutes: -1, maxMinutes: 1, confirmationRequired: false })
    await assert.rejects(estimateCollection({ keyword: 'java' }, false), /검색 건수/)
  } finally { globalThis.fetch = original }
})
