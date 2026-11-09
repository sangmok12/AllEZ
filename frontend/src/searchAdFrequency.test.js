import test from 'node:test'
import assert from 'node:assert/strict'
import { recordSearchForAd } from './searchAdFrequency.js'

function storage(initial) {
  let value = initial ?? null
  return { getItem: () => value, setItem: (_key, next) => { value = next } }
}

test('shows the search ad only on the fourth recorded search, including persisted counts', () => {
  const saved = storage()
  assert.deepEqual(Array.from({ length: 6 }, () => recordSearchForAd(saved)), [false, false, false, true, false, false])
  assert.equal(recordSearchForAd(storage('3')), true)
  assert.equal(recordSearchForAd(storage('4')), false)
})

test('invalid or unavailable browser storage never blocks searching', () => {
  for (const value of ['invalid', '-1', 'Infinity', '1.5']) {
    assert.equal(recordSearchForAd(storage(value)), false)
  }
  assert.equal(recordSearchForAd({ getItem() { throw new Error('disabled') } }), false)
  assert.equal(recordSearchForAd({ getItem: () => '3', setItem() { throw new Error('full') } }), false)
})
