import test from 'node:test'
import assert from 'node:assert/strict'
import { mergeSearchPage, nextSearchPages, searchResultTotal } from './searchResults.js'

const item = (source, postingId) => ({ source, postingId })
test('keeps results beyond 500 and deduplicates overlapping source pages', () => {
  const items = Array.from({ length: 500 }, (_, i) => item('JOBKOREA', String(i)))
  const previous = { items, sites: [{ source: 'JOBKOREA', nextPage: 10 }] }
  const result = mergeSearchPage(previous, {
    items: [item('JOBKOREA', '499'), item('JOBKOREA', '500'), item('SARAMIN', '500')],
    sites: [
      { source: 'JOBKOREA', status: 'SUCCESS', totalAvailable: 1000, nextPage: 11 },
      { source: 'SARAMIN', status: 'SUCCESS', totalAvailable: 1, nextPage: null },
    ],
  }, { keyword: 'Java' })
  assert.equal(result.items.length, 502)
  assert.deepEqual(result.items.slice(0, 500), items)
  assert.deepEqual(nextSearchPages(result), { jobKoreaPage: 11, saraminPage: null })
})

test('retries failed page while preserving completed source and submitted criteria', () => {
  const criteria = { keyword: 'Java' }
  const previous = {
    criteria, items: [item('SARAMIN', '1'), item('JOBKOREA', '2')],
    sites: [
      { source: 'SARAMIN', nextPage: null, totalAvailable: 1, status: 'SUCCESS' },
      { source: 'JOBKOREA', nextPage: 6, totalAvailable: 900, status: 'SUCCESS' },
    ],
  }
  const result = mergeSearchPage(previous, {
    items: [], sites: [{ source: 'JOBKOREA', nextPage: 6, status: 'FAILED', totalAvailable: 0 }],
  }, criteria)
  assert.equal(result.items.length, 2)
  assert.equal(result.sites.find((s) => s.source === 'JOBKOREA').totalAvailable, 900)
  assert.equal(result.criteria, criteria)
  assert.deepEqual(nextSearchPages(result), { jobKoreaPage: 6, saraminPage: null })
})

test('reports the full result count before all pages are fetched and exact distinct rows when complete', () => {
  const initial = {
    items: Array.from({ length: 100 }, (_, i) => item('JOBKOREA', String(i))),
    sites: [
      { source: 'JOBKOREA', status: 'SUCCESS', totalAvailable: 122, nextPage: 1 },
      { source: 'SARAMIN', status: 'SUCCESS', totalAvailable: 125, nextPage: 1 },
    ],
  }
  assert.equal(searchResultTotal(initial), 247)
  assert.equal(Math.ceil(searchResultTotal(initial) / 25), 10)
  const complete = { ...initial, sites: initial.sites.map((site) => ({ ...site, nextPage: null })) }
  assert.equal(searchResultTotal(complete), 100)
  const failure = mergeSearchPage(initial, { items: [], sites: [{ source: 'JOBKOREA', status: 'FAILED', totalAvailable: 0, nextPage: 1 }] }, {})
  assert.equal(searchResultTotal(failure), 247)
})
