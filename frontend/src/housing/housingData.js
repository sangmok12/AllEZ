import { apiUrl } from '../api.js'

// Phase 2 server contract; official API credentials must never enter this module.
export async function loadHousing({ signal, fetcher = fetch } = {}) {
  const response = await fetcher(apiUrl('/api/housing-subscriptions'), { signal, headers: { Accept: 'application/json' } })
  if (response.status === 404 || response.status === 501) return { status: 'not_connected', items: [] }
  if (!response.ok) throw new Error('청약정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')
  const data = await response.json()
  const dateOrNull = value => value == null || typeof value === 'string'
  const numberOrNull = value => value == null || (Number.isFinite(value) && value >= 0)
  if (!Array.isArray(data.items) || !data.items.every(item => item && typeof item.id === 'string' && typeof item.name === 'string'
    && typeof item.region === 'string' && typeof item.address === 'string'
    && item.id.trim() && item.name.trim() && numberOrNull(item.units)
    && dateOrNull(item.developer) && dateOrNull(item.noticeUrl)
    && ['announcementDate', 'specialStart', 'specialEnd', 'generalStart', 'generalEnd', 'winnerDate'].every(key => dateOrNull(item[key]))
    && (item.competition == null || (Array.isArray(item.competition) && item.competition.every(row => row && typeof row.name === 'string' && numberOrNull(row.ratio))))
    && dateOrNull(item.applicationStart) && dateOrNull(item.applicationEnd)
    && Array.isArray(item.houseTypes) && item.houseTypes.every(type => type && typeof type.name === 'string'
      && [type.priceMin, type.priceMax, type.units].every(numberOrNull)))) {
    throw new Error('청약정보 응답을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.')
  }
  if (new Set(data.items.map(item => item.id)).size !== data.items.length) throw new Error('청약정보에 중복된 식별자가 있어요. 다시 시도해 주세요.')
  return { status: 'ready', items: data.items, fetchedAt: data.fetchedAt || null }
}
