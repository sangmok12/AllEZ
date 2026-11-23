import test from 'node:test'
import assert from 'node:assert/strict'
import { koreaToday, validDate, statusOf, dayLabel, formatMoney, priceLabel, filterHousing, safeOfficialUrl } from './housing/housingModel.js'
import { housingExamples } from './housing/housingExamples.js'
import { loadHousing } from './housing/housingData.js'
const today = '2026-11-23'
const defaults = { keyword: '', region: '', status: 'all', price: '', sort: 'schedule' }

test('Korean dates keep the last application day open and reject invalid dates', () => {
  assert.equal(koreaToday(new Date('2026-11-22T15:10:00Z')), today)
  const item = { applicationStart: '2026-11-23', applicationEnd: '2026-11-25' }
  assert.equal(statusOf(item, '2026-11-22'), 'upcoming')
  assert.equal(dayLabel(item, '2026-11-22'), '접수까지 D-1')
  assert.equal(statusOf(item, '2026-11-25'), 'open')
  assert.equal(dayLabel(item, '2026-11-25'), '오늘 마감')
  assert.equal(statusOf(item, '2026-11-26'), 'closed')
  assert.equal(statusOf({ ...item, applicationStart: null }, today), 'unknown')
  assert.equal(validDate('2026-02-30'), false)
  assert.equal(statusOf({ applicationStart: '2026-12-01', applicationEnd: '2026-11-30' }, today), 'unknown')
})

test('filters combine region, name, status and an affordable housing type without treating missing prices as zero', () => {
  const items = housingExamples(today)
  assert.equal(filterHousing(items, defaults, today).length, 6)
  assert.equal(filterHousing(items, { ...defaults, region: '경기' }, today).length, 2)
  assert.deepEqual(filterHousing(items, { ...defaults, keyword: ' 내일숲 ', region: '경기', status: 'upcoming', price: '500000000' }, today).map(item => item.id), ['example-gyeonggi'])
  assert.equal(filterHousing(items, { ...defaults, price: '300000000' }, today)[0].id, 'example-daejeon')
  assert.equal(filterHousing(items, { ...defaults, keyword: '햇살', price: '1000000000' }, today).length, 0)
  assert.equal(priceLabel(items.at(-1)), '분양가 미공개')
  assert.equal(formatMoney(null), '미공개')
  assert.equal(formatMoney(590000000), '5억 9,000만원')
  assert.equal(filterHousing(items, { ...defaults, keyword: '없는단지' }, today).length, 0)
})

test('schedule sorting works in both directions and leaves unknown dates last', () => {
  const items = [...housingExamples(today), { id: 'unknown', name: '일정 없음', houseTypes: [] }]
  for (const sort of ['schedule', 'deadline', 'newest']) {
    const sorted = filterHousing(items, { ...defaults, sort }, today)
    assert.equal(sorted.at(-1).id, 'unknown')
    const key = sort === 'deadline' ? 'applicationEnd' : 'applicationStart'
    const dates = sorted.slice(0, -1).map(item => item[key])
    assert.deepEqual(dates, [...dates].sort((a, b) => sort === 'newest' ? b.localeCompare(a) : a.localeCompare(b)))
  }
})

test('official notice links reject script schemes, lookalike hosts and embedded credentials', () => {
  assert.ok(safeOfficialUrl('https://www.applyhome.co.kr/'))
  for (const url of ['javascript:alert(1)', 'https://applyhome.co.kr.evil.example/', 'https://evil.example/', 'https://user:pass@applyhome.co.kr/', null]) assert.equal(safeOfficialUrl(url), null)
})

test('unconnected and empty API states differ; errors never silently become example data', async () => {
  for (const status of [404, 501]) assert.deepEqual(await loadHousing({ fetcher: async () => new Response('', { status }) }), { status: 'not_connected', items: [] })
  assert.equal((await loadHousing({ fetcher: async () => Response.json({ items: [] }) })).status, 'ready')
  await assert.rejects(loadHousing({ fetcher: async () => new Response('', { status: 503 }) }))
  await assert.rejects(loadHousing({ fetcher: async () => { throw new Error('network') } }))
  await assert.rejects(loadHousing({ fetcher: async () => Response.json({ items: [{}] }) }))
  await assert.rejects(loadHousing({ fetcher: async () => new Response('<html>SPA fallback</html>') }))
  const controller = new AbortController()
  await loadHousing({ signal: controller.signal, fetcher: async (url, options) => {
    assert.match(url, /\/api\/housing-subscriptions$/)
    assert.equal(options.signal, controller.signal)
    assert.equal(options.headers.Authorization, undefined)
    return Response.json({ items: housingExamples(today) })
  } })
})
