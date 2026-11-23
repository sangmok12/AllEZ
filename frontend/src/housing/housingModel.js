export const REGIONS = ['서울', '경기', '인천', '부산', '대구', '광주', '대전', '울산', '세종', '강원', '충북', '충남', '전북', '전남', '경북', '경남', '제주']
export const STATUS_LABELS = { all: '전체', open: '접수 중', upcoming: '모집 예정', closed: '접수 마감', unknown: '일정 확인 중' }

export function koreaToday(now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now)
}
export function validDate(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value))
    && new Date(value).toISOString().slice(0, 10) === value
}
export function statusOf(item, today = koreaToday()) {
  if (!validDate(item.applicationStart) || !validDate(item.applicationEnd) || item.applicationEnd < item.applicationStart) return 'unknown'
  if (today < item.applicationStart) return 'upcoming'
  return today > item.applicationEnd ? 'closed' : 'open'
}
export function dayLabel(item, today = koreaToday()) {
  const status = statusOf(item, today)
  if (status === 'closed' || status === 'unknown') return STATUS_LABELS[status]
  const date = status === 'upcoming' ? item.applicationStart : item.applicationEnd
  const days = Math.round((Date.parse(date) - Date.parse(today)) / 86400000)
  if (!days) return status === 'open' ? '오늘 마감' : '오늘 시작'
  return `${status === 'upcoming' ? '접수까지' : '마감까지'} D-${days}`
}
export function formatDate(value) {
  if (!validDate(value)) return '미공개'
  return `${value.slice(0, 4)}.${value.slice(5, 7)}.${value.slice(8, 10)}`
}
export function formatMoney(value) {
  if (!Number.isFinite(value) || value < 0) return '미공개'
  if (value === 0) return '0원'
  const uk = Math.floor(value / 100000000)
  const man = Math.round((value % 100000000) / 10000)
  return [uk ? `${uk.toLocaleString()}억` : '', man ? `${man.toLocaleString()}만` : ''].filter(Boolean).join(' ') + '원'
}
export function priceRange(item) {
  const prices = (item.houseTypes || []).flatMap(type => [type.priceMin, type.priceMax]).filter(p => Number.isFinite(p) && p > 0)
  return prices.length ? [Math.min(...prices), Math.max(...prices)] : null
}
export function priceLabel(item) {
  const range = priceRange(item)
  if (!range) return '분양가 미공개'
  return range[0] === range[1] ? formatMoney(range[0]) : `${formatMoney(range[0])} ~ ${formatMoney(range[1])}`
}
export function filterHousing(items, filters, today = koreaToday()) {
  const keyword = filters.keyword.trim().normalize('NFKC').toLocaleLowerCase('ko')
  return items.filter(item => {
    const range = priceRange(item)
    return (!keyword || item.name.normalize('NFKC').toLocaleLowerCase('ko').includes(keyword))
      && (!filters.region || item.region === filters.region)
      && (filters.status === 'all' || statusOf(item, today) === filters.status)
      && (!filters.price || (range && range[0] <= Number(filters.price)))
  }).sort((a, b) => {
    const dateA = filters.sort === 'deadline' ? a.applicationEnd : a.applicationStart
    const dateB = filters.sort === 'deadline' ? b.applicationEnd : b.applicationStart
    const aValid = validDate(dateA), bValid = validDate(dateB)
    if (!aValid || !bValid) return Number(bValid) - Number(aValid)
    const comparison = dateA.localeCompare(dateB)
    return (filters.sort === 'newest' ? -comparison : comparison) || a.id.localeCompare(b.id)
  })
}
export function safeOfficialUrl(value) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !url.username && !url.password
      && ['applyhome.co.kr', 'data.go.kr', 'lh.or.kr'].some(host => url.hostname === host || url.hostname.endsWith(`.${host}`)) ? url.href : null
  } catch { return null }
}
