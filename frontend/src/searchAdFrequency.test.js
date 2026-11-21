import test from 'node:test'
import assert from 'node:assert/strict'
import { SEARCH_AD_COUNT_KEY, searchAdIsDue } from './searchAdFrequency.js'

const now = Date.parse('2026-11-21T14:59:00Z')
function fixture(raw = null) {
  const values = new Map(raw == null ? [] : [[SEARCH_AD_COUNT_KEY, raw]])
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) }
}

test('first three valid searches are free; fourth onward remains eligible across reloads and tabs', () => {
  const storage = fixture()
  // A new memory object represents a reload, new tab or category remount.
  assert.deepEqual(Array.from({ length: 6 }, () => searchAdIsDue(storage, {}, now)), [false, false, false, true, true, true])
  assert.equal(JSON.parse(storage.getItem(SEARCH_AD_COUNT_KEY)).count, 4)
})

test('free allowance resets at Korean midnight, independently of the ad-click cooldown', () => {
  const storage = fixture(JSON.stringify({ day: '2026-11-21', count: 4 }))
  const memory = {}
  assert.equal(searchAdIsDue(storage, memory, now), true)
  const midnight = Date.parse('2026-11-21T15:00:00Z')
  assert.deepEqual(Array.from({ length: 4 }, () => searchAdIsDue(storage, memory, midnight)), [false, false, false, true])
})

test('blocked and corrupt storage do not interrupt searches or skip the free allowance', () => {
  const blocked = { getItem() { throw Error('blocked') }, setItem() { throw Error('blocked') } }
  const memory = {}
  assert.deepEqual(Array.from({ length: 4 }, () => searchAdIsDue(blocked, memory, now)), [false, false, false, true])
  for (const raw of ['{bad', 'null', '{"day":"2026-11-21","count":-5}', '{"day":"2026-11-21","count":"3"}']) {
    assert.equal(searchAdIsDue(fixture(raw), {}, now), false)
  }
})
