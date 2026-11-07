import test from 'node:test'
import assert from 'node:assert/strict'
import { searchInputError } from './searchValidation.js'

test('part-time search accepts keyword, region, or both; rejects neither', () => {
  for (const [keyword, regionIds] of [['카페', []], ['', ['I000']], ['  ', ['I000_I010']], ['카페', ['I000_I010']]]) {
    assert.equal(searchInputError({ partTime: true, keyword, regionIds }), '')
  }
  assert.match(searchInputError({ partTime: true, keyword: '  ', regionIds: [] }), /검색어.*지역/)
  assert.match(searchInputError({ partTime: true, keyword: '가'.repeat(21), regionIds: ['I000'] }), /20자/)
})

test('career search still requires a keyword even with a region', () => {
  assert.equal(searchInputError({ partTime: false, keyword: '', regionIds: ['I000'] }), '검색어를 입력해 주세요.')
})
