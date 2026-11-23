import { useEffect, useMemo, useState } from 'react'
import { loadHousing } from './housing/housingData.js'
import { housingExamples } from './housing/housingExamples.js'
import { REGIONS, STATUS_LABELS, koreaToday, statusOf, dayLabel, formatDate, formatMoney, priceLabel, filterHousing, safeOfficialUrl } from './housing/housingModel.js'
import './HousingSubscriptions.css'

const DEFAULT_FILTERS = { keyword: '', region: '', status: 'all', price: '', sort: 'schedule' }
const countLabel = value => Number.isFinite(value) ? `${value.toLocaleString()}세대` : '미공개'

function HousingCard({ item, today, example }) {
  const status = statusOf(item, today), notice = safeOfficialUrl(item.noticeUrl)
  return <article className="housing-card">
    <div className="housing-card-top"><span className={`housing-status is-${status}`}>{STATUS_LABELS[status]}</span><span className="housing-dday">{dayLabel(item, today)}</span></div>
    <p className="housing-card-region">{item.region} · 아파트{example ? ' · UI 예시' : ''}</p>
    <h3>{item.name}</h3><p className="housing-address">{item.address}</p>
    <div className="housing-card-facts"><div><span>공급 규모</span><strong>{countLabel(item.units)}</strong></div><div><span>분양가</span><strong>{priceLabel(item)}</strong></div></div>
    <p className="housing-period"><span>청약 접수</span><strong>{formatDate(item.applicationStart)} ~ {formatDate(item.applicationEnd)}</strong></p>
    <details className="housing-details"><summary><span>일정·주택형 자세히 보기</span><span aria-hidden="true">⌄</span></summary>
      <div className="housing-detail-content"><h4>청약 일정</h4><dl className="housing-schedule">
        <div><dt>모집공고일</dt><dd>{formatDate(item.announcementDate)}</dd></div>
        <div><dt>특별공급</dt><dd>{formatDate(item.specialStart)}{item.specialEnd && item.specialEnd !== item.specialStart ? ` ~ ${formatDate(item.specialEnd)}` : ''}</dd></div>
        <div><dt>일반공급</dt><dd>{formatDate(item.generalStart)}{item.generalEnd && item.generalEnd !== item.generalStart ? ` ~ ${formatDate(item.generalEnd)}` : ''}</dd></div>
        <div><dt>당첨자 발표</dt><dd>{formatDate(item.winnerDate)}</dd></div>
      </dl><h4>주택형별 공급·분양가</h4><p className="housing-detail-note">분양가는 주택형별 총액 기준. 층·옵션에 따라 달라질 수 있어요.</p>
        {item.houseTypes.length ? <div className="housing-type-list">{item.houseTypes.map((type, index) => <div key={`${type.name}-${index}`}><strong>{type.name}</strong><span>{countLabel(type.units)}</span><span>{priceLabel({ houseTypes: [type] })}</span></div>)}</div> : <p className="housing-detail-note">주택형 정보가 아직 공개되지 않았어요.</p>}
        <h4>청약 경쟁률</h4>{item.competition?.length ? <div className="housing-competition">{item.competition.map((row, index) => <span key={`${row.name}-${index}`}>{row.name} <strong>{Number.isFinite(row.ratio) ? `${row.ratio.toLocaleString()} : 1` : '미공개'}</strong></span>)}</div> : <p className="housing-detail-note">아직 발표되지 않았거나 제공된 정보가 없어요.</p>}
        <h4>주변 시세</h4><p className="housing-detail-note">비교 가능한 공식 시세 데이터가 연결되지 않았어요.</p>
        {item.developer && <p className="housing-detail-note">사업 주체 · {item.developer}</p>}
        {notice && !example ? <a className="housing-official-link" href={notice} target="_blank" rel="noreferrer">공식 모집공고 확인 ↗</a> : <p className="housing-detail-note">{example ? '가상의 예시 단지로, 실제 모집공고가 없어요.' : '공식 모집공고 링크가 제공되지 않았어요.'}</p>}
      </div>
    </details>
  </article>
}

export default function HousingSubscriptions() {
  const [data, setData] = useState({ status: 'loading', items: [] })
  const [attempt, setAttempt] = useState(0)
  const [example, setExample] = useState(false)
  const [filters, setFilters] = useState(DEFAULT_FILTERS)
  const [today, setToday] = useState(koreaToday)
  useEffect(() => { const timer = setInterval(() => setToday(koreaToday()), 60000); return () => clearInterval(timer) }, [])
  useEffect(() => {
    const controller = new AbortController(), signal = AbortSignal.any([controller.signal, AbortSignal.timeout(15000)])
    loadHousing({ signal }).then(result => { if (!controller.signal.aborted) setData(result) })
      .catch(() => { if (!controller.signal.aborted) setData({ status: 'error', items: [] }) })
    return () => controller.abort()
  }, [attempt])
  const examples = useMemo(() => example ? housingExamples(today) : [], [example, today])
  const items = example ? examples : data.items, ready = example || data.status === 'ready'
  const visible = filterHousing(items, filters, today)
  const counts = items.reduce((acc, item) => { acc[statusOf(item, today)]++; return acc }, { open: 0, upcoming: 0, closed: 0, unknown: 0 })
  const setFilter = (key, value) => setFilters(current => ({ ...current, [key]: value }))
  const retry = () => { setData({ status: 'loading', items: [] }); setAttempt(value => value + 1) }
  const toggleExample = () => { setExample(value => !value); setFilters(DEFAULT_FILTERS) }
  const hasFilters = filters.keyword || filters.region || filters.status !== 'all' || filters.price
  return <div className="housing-page">
    <section className="housing-intro"><p className="housing-kicker">AllEZ 청약정보</p><h1>내 집을 향한 기회,<br /><strong>한눈에 놓치지 않게.</strong></h1><p>지역부터 일정, 분양가까지.<br className="housing-mobile-break" /> 복잡한 청약정보를 쉽게 살펴보세요.</p></section>
    {example ? <div className="housing-data-banner is-example" role="status"><div><strong>예시 화면을 보고 있어요</strong><p>단지·일정·금액·경쟁률은 모두 가상의 UI 예시이며 실제 청약정보가 아니에요.</p></div><button type="button" onClick={toggleExample}>예시 닫기</button></div>
      : data.status === 'not_connected' ? <div className="housing-data-banner"><div><strong>공식 청약정보 연결을 준비하고 있어요</strong><p>실제 공고는 아직 표시되지 않아요. 화면과 필터를 예시로 미리 둘러볼 수 있어요.</p></div><button type="button" onClick={toggleExample}>예시로 둘러보기</button></div> : null}
    <section className="housing-overview" aria-label="전체 청약 현황">
      {[['all', '전체 청약', items.length], ['open', '접수 중', counts.open], ['upcoming', '모집 예정', counts.upcoming], ['closed', '접수 마감', counts.closed]].map(([status, title, count]) => <button key={status} type="button" aria-pressed={filters.status === status} onClick={() => setFilter('status', status)} disabled={!ready}><span>{title}</span><strong>{ready ? count.toLocaleString() : '—'}{ready && <small>건</small>}</strong></button>)}
    </section>
    <section className="housing-filter-section" aria-label="청약 검색 조건"><form className="housing-filter-form" onSubmit={event => event.preventDefault()}>
      <label className="housing-name-filter"><span>단지명</span><input type="search" placeholder="찾고 싶은 단지명을 입력하세요" value={filters.keyword} onChange={event => setFilter('keyword', event.target.value)} /></label>
      <label><span>지역</span><select aria-label="지역" value={filters.region} onChange={event => setFilter('region', event.target.value)}><option value="">전국</option>{REGIONS.map(region => <option key={region}>{region}</option>)}</select></label>
      <label><span>모집 상태</span><select aria-label="모집 상태" value={filters.status} onChange={event => setFilter('status', event.target.value)}>{Object.entries(STATUS_LABELS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <label><span>분양가</span><select aria-label="분양가" value={filters.price} onChange={event => setFilter('price', event.target.value)}><option value="">전체 금액</option>{[3, 5, 7, 10].map(price => <option key={price} value={price * 100000000}>{price}억 원 이하</option>)}</select></label>
    </form><p className="housing-filter-note">분양가 필터는 해당 금액 이하의 주택형이 있는 단지를 보여줘요.</p>
      {ready && items.length > 0 && <div className="housing-region-overview" aria-label="지역별 청약 현황"><span>지역별 현황</span><button type="button" aria-pressed={!filters.region} onClick={() => setFilter('region', '')}>전국 {items.length}</button>{REGIONS.filter(region => items.some(item => item.region === region)).map(region => <button key={region} type="button" aria-pressed={filters.region === region} onClick={() => setFilter('region', filters.region === region ? '' : region)}>{region} {items.filter(item => item.region === region).length}</button>)}</div>}
    </section>
    <section className="housing-results" aria-labelledby="housing-results-title" aria-busy={!example && data.status === 'loading'}>
      <div className="housing-results-heading"><div><h2 id="housing-results-title">{example ? '예시 청약 목록' : '청약 목록'}</h2><span role="status">{ready ? `${visible.length.toLocaleString()}건` : data.status === 'not_connected' ? '공식 데이터 연결 전' : data.status === 'error' ? '조회 실패' : '확인 중'}</span></div><label><span className="housing-sr-only">청약 정렬</span><select aria-label="청약 정렬" value={filters.sort} onChange={event => setFilter('sort', event.target.value)}><option value="schedule">접수 시작일순</option><option value="deadline">접수 마감일순</option><option value="newest">접수 시작일 최신순</option></select></label></div>
      {hasFilters && ready && <div className="housing-active-filters"><span>{[filters.region || '전국', STATUS_LABELS[filters.status], filters.price ? `${formatMoney(Number(filters.price))} 이하` : '', filters.keyword ? `“${filters.keyword}”` : ''].filter(Boolean).join(' · ')}</span><button type="button" onClick={() => setFilters(DEFAULT_FILTERS)}>조건 초기화</button></div>}
      {!example && data.status === 'loading' ? <div className="housing-empty" role="status"><span className="housing-loading-dot" /><h3>청약정보를 불러오고 있어요</h3><p>공식 데이터 연결 상태를 확인하고 있어요.</p></div>
        : !example && data.status === 'error' ? <div className="housing-empty" role="alert"><h3>청약정보를 불러오지 못했어요</h3><p>잠시 후 다시 시도해 주세요. 실제 정보 대신 예시가 자동으로 표시되지는 않아요.</p><div className="housing-empty-actions"><button type="button" onClick={retry}>다시 시도</button><button type="button" onClick={toggleExample}>예시로 둘러보기</button></div></div>
          : !ready ? <div className="housing-empty"><HouseIcon /><h3>청약의 시작, 조금 더 쉽게</h3><p>공식 정보가 연결되면 전국의 청약 일정과<br />분양정보를 이곳에서 확인할 수 있어요.</p><div className="housing-empty-actions"><button type="button" onClick={toggleExample}>예시로 둘러보기</button><a href="https://www.applyhome.co.kr/" target="_blank" rel="noreferrer">청약홈 바로가기 ↗</a></div></div>
            : visible.length ? <div className="housing-card-grid">{visible.map(item => <HousingCard key={item.id} item={item} today={today} example={example} />)}</div>
              : <div className="housing-empty"><HouseIcon /><h3>{items.length ? '조건에 맞는 청약이 없어요' : '조회된 청약정보가 없어요'}</h3><p>{items.length ? '지역이나 분양가 조건을 바꿔 다시 살펴보세요.' : '새 공고가 공개되면 이곳에서 확인할 수 있어요.'}</p>{hasFilters && <button type="button" onClick={() => setFilters(DEFAULT_FILTERS)}>조건 초기화</button>}</div>}
    </section>
    <aside className="housing-bottom-note"><strong>청약 전, 꼭 확인하세요</strong><p>청약 자격과 최종 일정·분양가는 공식 모집공고가 기준이에요. 계약 조건과 공급 세대수도 공고문에서 확인해 주세요.</p><a href="https://www.applyhome.co.kr/" target="_blank" rel="noreferrer">청약홈에서 공식 정보 확인 ↗</a></aside>
  </div>
}
function HouseIcon() {
  return <svg className="housing-house-icon" width="64" height="64" viewBox="0 0 64 64" fill="none" aria-hidden="true"><rect x="7" y="5" width="50" height="54" rx="17" fill="currentColor" opacity=".08" /><path d="M16 31 32 18l16 13M21 28v20h22V28M29 48V36h7v12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
}
