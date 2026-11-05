import test from 'node:test'
import assert from 'node:assert/strict'
import { getAnalyticsSession, SESSION_TIMEOUT } from './analyticsSession.js'

function fixture() {
  const data = new Map()
  return { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) }
}
const ids = [
  '11111111-1111-4111-8111-111111111111',
  '22222222-2222-4222-8222-222222222222',
  '33333333-3333-4333-8333-333333333333',
]
test('reloads and other tabs reuse the visitor and active session; idle visits create a new session', () => {
  const storage = fixture()
  let next = 0
  const makeId = () => ids[next++]
  const first = getAnalyticsSession(storage, {}, 1000, makeId)
  const reload = getAnalyticsSession(storage, {}, 2000, makeId)
  assert.deepEqual(reload, first)
  const secondVisit = getAnalyticsSession(storage, {}, 2000 + SESSION_TIMEOUT, makeId)
  assert.equal(secondVisit.visitorId, first.visitorId)
  assert.notEqual(secondVisit.sessionId, first.sessionId)
  assert.equal(next, 3)
})
test('blocked or malformed storage does not break tracking', () => {
  const memory = {}
  let next = 0
  const blocked = { getItem: () => { throw Error('blocked') }, setItem: () => { throw Error('blocked') } }
  const first = getAnalyticsSession(blocked, memory, 1000, () => ids[next++])
  assert.deepEqual(getAnalyticsSession(blocked, memory, 2000, () => ids[next++]), first)
  const malformed = { getItem: () => '{bad', setItem: () => {} }
  assert.deepEqual(getAnalyticsSession(malformed, memory, 3000, () => ids[next++]), first)
})
