import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import './JobSearch.css'
import regions from './regions.json'
import { startUsageTracking, trackSearch, trackPosting } from './usageAnalytics'
import PartTimeRegionSelect from './PartTimeRegionSelect'
import { HomeScreenHint } from './MobileAccess'
import SearchAdDialog from './SearchAdDialog'
import { adsAreHidden, subscribeAdCooldown } from './adCooldown'
import { searchInputError } from './searchValidation'
import { collectPartTime } from './partTimeCollection'
import { collectJobs } from './jobCollection'

import { READ_HISTORY_KEY, postingKey, readHistory, rememberPosting, confirmationLabel } from './readHistory'

const CAREERS = [['ANY', '경력 전체'], ['NEWCOMER', '신입'], ['EXPERIENCED', '경력'], ['NO_PREFERENCE', '경력무관']]
const EDUCATIONS = [['ANY', '학력 전체'], ['NO_PREFERENCE', '학력무관'], ['HIGH_SCHOOL', '고졸 이상'], ['COLLEGE', '전문대졸 이상'], ['UNIVERSITY', '대졸 이상'], ['MASTER', '석사 이상'], ['DOCTOR', '박사']]
const EMPLOYMENTS = [['PERMANENT', '정규직'], ['CONTRACT', '계약직'], ['INTERN', '인턴'], ['DISPATCH', '파견직'], ['FREELANCE', '프리랜서'], ['PART_TIME', '아르바이트']]
const PAGE_SIZE = 25

function JobSearch({ partTime = false }) {
  const category = partTime ? 'part_time' : 'jobs'
  const [keyword, setKeyword] = useState('')
  const [regionIds, setRegionIds] = useState([])
  const [locations, setLocations] = useState([])
  const [careerType, setCareerType] = useState('ANY')
  const [minimumCareerYears, setMinimumCareerYears] = useState('')
  const [maximumCareerYears, setMaximumCareerYears] = useState('')
  const [minimumEducation, setMinimumEducation] = useState('ANY')
  const [employmentTypes, setEmploymentTypes] = useState([])
  const [state, setState] = useState({ status: 'idle', message: '' })
  const [result, setResult] = useState(null)
  const [showSearchAd, setShowSearchAd] = useState(false)
  const hideAds = useSyncExternalStore(subscribeAdCooldown, adsAreHidden, () => false)
  // A completed cooldown should wait for another search before opening a dialog.
  if (hideAds && showSearchAd) setShowSearchAd(false)
  const [page, setPage] = useState(1)
  const [readPostings, setReadPostings] = useState(() => {
    try { return readHistory(window.localStorage) } catch { return {} }
  })
  const [readStorageFailed, setReadStorageFailed] = useState(false)

  useEffect(() => {
    const syncHistory = (event) => {
      if (event.key === READ_HISTORY_KEY || event.key === null) {
        try { setReadPostings(readHistory(window.localStorage)) } catch { /* Storage may be disabled. */ }
      }
    }
    window.addEventListener('storage', syncHistory)
    return () => window.removeEventListener('storage', syncHistory)
  }, [])

  useEffect(() => startUsageTracking(category), [category])

  const markRead = (item) => {
    try { trackPosting(item, result.criteria.searchId, category) } catch { /* Analytics cannot block opening a posting. */ }
    try {
      setReadPostings(rememberPosting(window.localStorage, item))
      setReadStorageFailed(false)
    } catch {
      setReadStorageFailed(true)
    }
  }

  const busy = useRef(false)
  const regionFilter = useRef(null)
  const collectionController = useRef(null)
  useEffect(() => () => collectionController.current?.abort(), [])

  const visibleItems = useMemo(() => result?.items.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE) || [], [result, page])
  const totalResults = result?.items.length || 0
  const totalPages = Math.max(1, Math.ceil(totalResults / PAGE_SIZE))

  useEffect(() => {
    const outside = (event) => {
      if (regionFilter.current?.open && !regionFilter.current.contains(event.target)) regionFilter.current.open = false
    }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [])

  const closeRegions = () => {
    if (!regionFilter.current) return
    regionFilter.current.open = false
    regionFilter.current.querySelector('summary')?.focus()
  }

  const toggleRegion = (region, area) => {
    const id = area?.id || region.id
    setRegionIds((current) => {
      const groupIds = [region.id, ...region.areas.map((item) => item.id)]
      if (current.includes(id)) return current.filter((item) => item !== id)
      if (!area) return [...current.filter((item) => !groupIds.includes(item)), id]
      return [...current.filter((item) => item !== region.id), id]
    })
  }

  const changeCareerType = (value) => {
    setCareerType(value)
    if (value !== 'EXPERIENCED') {
      setMinimumCareerYears('')
      setMaximumCareerYears('')
    }
  }

  const changeCareerYears = (setter, value) => {
    setter(value)
    if (value !== '') setCareerType('EXPERIENCED')
  }

  const collectAll = async (criteria) => {
    busy.current = true
    const controller = new AbortController()
    collectionController.current = controller
    setState({ status: 'loading', phase: 'checking', message: '검색 결과를 확인하고 있어요' })
    try {
      const collect = partTime ? collectPartTime : collectJobs
      const data = await collect(criteria, (update) => {
        setState({ status: 'loading', ...update })
      }, AbortSignal.any([controller.signal, AbortSignal.timeout(240000)]))
      if (controller.signal.aborted) return
      setResult({ ...data, criteria })
      setPage(1)
      setState({ status: 'success', message: '' })
    } catch (error) {
      setState({ status: controller.signal.aborted ? 'idle' : 'error', message: controller.signal.aborted ? ''
        : error.name === 'TimeoutError' ? '수집 시간이 길어지고 있습니다. 범위를 좁혀 다시 검색해 주세요.'
          : error.message || '검색 중 오류가 발생했습니다.' })
    } finally {
      collectionController.current = null
      busy.current = false
    }
  }

  const submit = async (event) => {
    event.preventDefault()
    if (busy.current) return
    event.currentTarget.querySelectorAll('details[open]').forEach((element) => { element.open = false })
    const selectedLocations = locations.filter(area => area.regionId)
    const selectedIds = partTime ? [...new Set(selectedLocations.map(area => area.regionId))] : regionIds
    const inputError = searchInputError({ partTime, keyword, regionIds: selectedIds })
    if (inputError) return setState({ status: 'error', message: inputError })
    if (minimumCareerYears && maximumCareerYears && Number(minimumCareerYears) > Number(maximumCareerYears)) {
      return setState({ status: 'error', message: '최소 경력은 최대 경력보다 클 수 없습니다.' })
    }
    const criteria = {
      searchId: crypto.randomUUID(),
      keyword: keyword.trim(), regionIds: [...selectedIds], careerType,
      locations: partTime ? selectedLocations : undefined,
      neighborhoodId: partTime && selectedLocations.length === 1 ? selectedLocations[0].neighborhoodId : undefined,
      neighborhoodName: partTime && selectedLocations.length === 1 ? selectedLocations[0].neighborhoodName : undefined,
      minimumCareerYears: minimumCareerYears === '' ? null : Number(minimumCareerYears),
      maximumCareerYears: maximumCareerYears === '' ? null : Number(maximumCareerYears),
      minimumEducation, employmentTypes: [...employmentTypes],
    }
    try { Object.assign(criteria, trackSearch(criteria, category)) } catch { /* Search does not depend on analytics. */ }
    if (!hideAds) setShowSearchAd(true)
    setResult(null)
    setPage(1)
    await collectAll(criteria)
  }

  return (
    <div className="job-search-page" aria-busy={state.status === 'loading'}>
      {state.status === 'loading' && (
        <div className="loading-scene" role="status" aria-live="polite">
          <div className="loading-card">
            <div className="loading-brand" aria-label="AllEZ"><span className="brand-wordmark"><span className="brand-all">All</span><span className="brand-ez">EZ</span></span></div>
            <span className="loading-light" aria-hidden="true" />
            <p>
              {state.phase === 'sorting' ? '최신순으로 정렬 중' : state.phase === 'checking' ? '검색 결과를 확인하고 있어요' : partTime ? '두 알바사이트의 공고를 모으고 있어요' : '두 채용사이트의 공고를 모으고 있어요'}
              <span className="loading-dots" aria-hidden="true"><i>.</i><i>.</i><i>.</i></span>
            </p>
            <div className="collection-progress">
              {state.total != null && <>
                <strong>{Math.min(state.collected || 0, state.total).toLocaleString()} / {state.total.toLocaleString()}건 수집{state.phase === 'sorting' ? ' 완료' : ' 중'}</strong>
                <progress aria-label="공고 수집 진행률" value={Math.min(state.collected || 0, state.total)} max={state.total || 1} />
              </>}
              <button type="button" onClick={() => collectionController.current?.abort()}>검색 취소</button>
            </div>
          </div>
        </div>
      )}
      <section className="search-intro">
        <h1>당신의 시간은 <strong>소중하니까.</strong></h1>
        <p>여기저기 흩어진 일자리, 한곳에 모았어요.</p>
      </section>

      <form className="search-form" onSubmit={submit}>
        <div className="search-keyword-row">
          <input value={keyword} onChange={(event) => setKeyword(event.target.value)} maxLength={partTime ? 20 : 100} placeholder={partTime ? "검색어 없이 동네만 골라도 돼요" : "직무, 기술, 회사명으로 검색"} aria-label={partTime ? "알바공고 검색어 (지역 선택 시 생략 가능)" : "채용공고 검색어"} />
          <button type="submit" disabled={state.status === 'loading'}>통합검색 <span>→</span></button>
        </div>
        <div className={`search-filters${partTime ? ' part-time-filters' : ''}`}>
          {partTime ? <PartTimeRegionSelect value={locations} onChange={setLocations} /> : <>
          <details className="region-filter" ref={regionFilter} onKeyDown={(event) => { if (event.key === 'Escape') { event.preventDefault(); closeRegions() } }}>
            <summary><span>지역 {regionIds.length > 0 && <b>{regionIds.length}</b>}</span><span className="region-toggle"><span className="region-closed-label">선택 ⌄</span><span className="region-open-label">접기 ⌃</span></span></summary>
            <div className="region-panel">
              <div className="region-panel-heading"><strong>지역 선택</strong><button type="button" onClick={closeRegions}>닫기 ×</button></div>
              <div className="region-groups">
              {regions.map((region) => (
                <div className="region-group" key={region.id}>
                  <label className="province"><input type="checkbox" checked={regionIds.includes(region.id)} onChange={() => toggleRegion(region)} />{region.name} 전체</label>
                  <div>{region.areas.map((area) => <label key={area.id}><input type="checkbox" checked={regionIds.includes(area.id)} onChange={() => toggleRegion(region, area)} />{area.name}</label>)}</div>
                </div>
              ))}
              </div>
              <button type="button" className="region-panel-done" onClick={closeRegions}>선택 완료</button>
            </div>
          </details>
          <label>경력<select value={careerType} onChange={(event) => changeCareerType(event.target.value)}>{CAREERS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <label>최소 연차<input type="number" min="0" max="30" value={minimumCareerYears} onChange={(event) => changeCareerYears(setMinimumCareerYears, event.target.value)} placeholder="전체" /></label>
          <label>최대 연차<input type="number" min="0" max="30" value={maximumCareerYears} onChange={(event) => changeCareerYears(setMaximumCareerYears, event.target.value)} placeholder="전체" /></label>
          <label>학력<select value={minimumEducation} onChange={(event) => setMinimumEducation(event.target.value)}>{EDUCATIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <details className="employment-filter">
            <summary>고용형태 {employmentTypes.length > 0 && <b>{employmentTypes.length}</b>}</summary>
            <div>{EMPLOYMENTS.map(([value, label]) => <label key={value}><input type="checkbox" checked={employmentTypes.includes(value)} onChange={() => setEmploymentTypes((current) => current.includes(value) ? current.filter((item) => item !== value) : [...current, value])} />{label}</label>)}</div>
          </details>
          </>}
        </div>
        <p className="search-guide">익명 방문·검색·클릭 기록을 수집합니다. 개인정보는 입력하지 마세요.</p>
        {state.message && state.status !== 'loading' && <div className={`notice ${state.status}`}>{state.message}</div>}
      </form>

      <HomeScreenHint />

      {!hideAds && showSearchAd && <SearchAdDialog onClose={() => setShowSearchAd(false)} />}

      {result && (
        <section className="search-results">
          <div className="search-result-heading">
            <div><h2>{partTime ? '통합 알바공고' : '통합 채용공고'}</h2></div>
            <strong>검색 결과 총 {totalResults.toLocaleString()}건</strong>
          </div>
          {readStorageFailed && <p className="read-history-guide" role="status">브라우저 저장 공간을 사용할 수 없어 읽음 기록을 저장하지 못했습니다.</p>}
          {visibleItems.length ? <div className="job-result-list">{visibleItems.map((item) => (
            <article className={`job-result-card${readPostings[postingKey(item)] ? ' is-read' : ''}`} key={`${item.source}-${item.postingId}`}>
              <div className="job-card-top">
                <span className={`source-chip ${item.source.toLowerCase()}`}>{item.sourceLabel}</span>
                <div className="job-card-dates"><span>{partTime ? `등록 ${item.registeredDate || '일자 미정'}` : `${item.registeredDate || '등록일 미정'} ~ ${item.deadline || '미정'}`}</span></div>
              </div>
              <p className="job-company">{item.companyName}</p>
              {item.payText && <p className="job-pay">{item.payText}</p>}
              <div className="job-title-row">
                <h3><a href={item.url} target="_blank" rel="noreferrer" onClick={() => markRead(item)} onAuxClick={(event) => { if (event.button === 1) markRead(item) }}>{item.title}</a></h3>
              </div>
              {readPostings[postingKey(item)] && <time className="job-read-date" dateTime={readPostings[postingKey(item)]}>{confirmationLabel(readPostings[postingKey(item)])}</time>}
              <div className="job-meta">{item.locations?.length > 0 && <span>📍 {item.locations.join(', ')}</span>}{item.career && <span>{item.career}</span>}{item.education && <span>{item.education}</span>}{item.employmentTypes?.length > 0 && <span>{item.employmentTypes.join(' · ')}</span>}</div>
              {item.skills?.length > 0 && <div className="skill-tags">{item.skills.slice(0, 8).map((skill) => <span key={skill}>{skill}</span>)}</div>}
            </article>
          ))}</div> : <p className="empty-search">조건에 맞는 공고를 찾지 못했습니다.</p>}
          {(totalPages > 1) && <div className="pagination">
            <button disabled={page === 1 || state.status === 'loading'} onClick={() => setPage((value) => value - 1)}>이전</button>
            <span>{page} / {totalPages}</span>
            <button disabled={state.status === 'loading' || page >= totalPages} onClick={() => setPage((value) => value + 1)}>다음</button>
          </div>}
        </section>
      )}
    </div>
  )
}

export default JobSearch
