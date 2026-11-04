import { useMemo, useRef, useState } from 'react'
import './JobSearch.css'
import { apiUrl } from './api'
import regions from './regions.json'
import { mergeSearchPage, nextSearchPages } from './searchResults'

const CAREERS = [['ANY', '경력 전체'], ['NEWCOMER', '신입'], ['EXPERIENCED', '경력'], ['NO_PREFERENCE', '경력무관']]
const EDUCATIONS = [['ANY', '학력 전체'], ['NO_PREFERENCE', '학력무관'], ['HIGH_SCHOOL', '고졸 이상'], ['COLLEGE', '전문대졸 이상'], ['UNIVERSITY', '대졸 이상'], ['MASTER', '석사 이상'], ['DOCTOR', '박사']]
const EMPLOYMENTS = [['PERMANENT', '정규직'], ['CONTRACT', '계약직'], ['INTERN', '인턴'], ['DISPATCH', '파견직'], ['FREELANCE', '프리랜서'], ['PART_TIME', '아르바이트']]
const PAGE_SIZE = 25

function JobSearch() {
  const [keyword, setKeyword] = useState('')
  const [regionIds, setRegionIds] = useState([])
  const [careerType, setCareerType] = useState('ANY')
  const [minimumCareerYears, setMinimumCareerYears] = useState('')
  const [maximumCareerYears, setMaximumCareerYears] = useState('')
  const [minimumEducation, setMinimumEducation] = useState('ANY')
  const [employmentTypes, setEmploymentTypes] = useState([])
  const [state, setState] = useState({ status: 'idle', message: '' })
  const [result, setResult] = useState(null)
  const [page, setPage] = useState(1)
  const busy = useRef(false)
  const hasMore = result?.sites.some((site) => site.nextPage !== null)
  const failed = result?.sites.some((site) => site.status === 'FAILED')

  const visibleItems = useMemo(() => result?.items.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE) || [], [result, page])
  const totalPages = Math.max(1, Math.ceil((result?.items.length || 0) / PAGE_SIZE))

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

  const loadPage = async (criteria, cursors, previous = null) => {
    if (busy.current) return
    busy.current = true
    setState({ status: 'loading', message: '공고를 불러오고 있습니다. 오랜만에 검색하면 준비에 1분 정도 걸릴 수 있습니다.' })
    try {
      const response = await fetch(apiUrl('/api/job-search/pages'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ criteria: { ...criteria, searchId: undefined }, searchId: criteria.searchId, ...cursors }),
        signal: AbortSignal.timeout(150000),
      })
      const data = await response.json().catch(() => {
        throw new Error('검색 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.')
      })
      if (!response.ok) throw new Error(data.detail || '검색 요청을 처리하지 못했습니다.')
      if (!Array.isArray(data.items) || !Array.isArray(data.sites)) {
        throw new Error('검색 응답을 확인하지 못했습니다. 다시 시도해 주세요.')
      }
      const merged = mergeSearchPage(previous, data, criteria)
      setResult(merged)
      const firstNewPage = Math.floor((previous?.items.length || 0) / PAGE_SIZE) + 1
      setPage(Math.min(firstNewPage, Math.max(1, Math.ceil(merged.items.length / PAGE_SIZE))))
      const failures = merged.sites.filter((site) => site.status === 'FAILED')
      setState({
        status: failures.length ? 'error' : 'success',
        message: failures.length
          ? `${failures.map((site) => site.sourceLabel).join(', ')} 공고를 불러오지 못했습니다. 기존 결과는 유지됩니다. 아래에서 다시 불러와 주세요.`
          : `현재 ${merged.items.length.toLocaleString()}개의 공고를 불러왔습니다.`,
      })
    } catch (error) {
      if (!previous) {
        setResult({
          criteria, items: [],
          sites: [
            { source: 'JOBKOREA', sourceLabel: '잡코리아', status: 'FAILED', returnedCount: 0, totalAvailable: 0, nextPage: cursors.jobKoreaPage },
            { source: 'SARAMIN', sourceLabel: '사람인', status: 'FAILED', returnedCount: 0, totalAvailable: 0, nextPage: cursors.saraminPage },
          ],
        })
      }
      setState({ status: 'error', message: error.name === 'TimeoutError'
        ? '응답이 늦어지고 있습니다. 아래에서 다시 불러와 주세요.'
        : error.message || '검색 중 오류가 발생했습니다.' })
    } finally {
      busy.current = false
    }
  }

  const submit = async (event) => {
    event.preventDefault()
    if (busy.current) return
    if (!keyword.trim()) return setState({ status: 'error', message: '검색어를 입력해 주세요.' })
    if (minimumCareerYears && maximumCareerYears && Number(minimumCareerYears) > Number(maximumCareerYears)) {
      return setState({ status: 'error', message: '최소 경력은 최대 경력보다 클 수 없습니다.' })
    }
    const criteria = {
      searchId: crypto.randomUUID(),
      keyword: keyword.trim(), regionIds: [...regionIds], careerType,
      minimumCareerYears: minimumCareerYears === '' ? null : Number(minimumCareerYears),
      maximumCareerYears: maximumCareerYears === '' ? null : Number(maximumCareerYears),
      minimumEducation, employmentTypes: [...employmentTypes],
    }
    setResult(null)
    setPage(1)
    await loadPage(criteria, { jobKoreaPage: 0, saraminPage: 0 })
  }

  const loadMore = () => {
    if (result && hasMore) return loadPage(result.criteria, nextSearchPages(result), result)
  }

  return (
    <div className="job-search-page" aria-busy={state.status === 'loading'}>
      {state.status === 'loading' && (
        <div className="loading-scene" role="status" aria-live="polite">
          <div className="loading-card">
            <div className="loading-brand" aria-label="JobFit AX">JobFit <strong>AX</strong></div>
            <span className="loading-light" aria-hidden="true" />
            <p>
              {result ? '다음 공고를 불러오고 있어요' : '두 채용사이트의 공고를 모으고 있어요'}
              <span className="loading-dots" aria-hidden="true"><i>.</i><i>.</i><i>.</i></span>
            </p>
          </div>
        </div>
      )}
      <section className="search-intro">
        <span className="eyebrow">INTEGRATED JOB SEARCH</span>
        <h1>두 채용사이트의 공고를<br /><strong>한 번에</strong> 찾아보세요.</h1>
        <p>같은 조건으로 잡코리아와 사람인을 검색하고, 공고를 한 화면에서 비교하고 원문을 확인하세요.</p>
      </section>

      <form className="search-form" onSubmit={submit}>
        <div className="search-keyword-row">
          <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="직무, 기술, 회사명으로 검색" aria-label="채용공고 검색어" />
          <button type="submit" disabled={state.status === 'loading'}>통합검색 <span>→</span></button>
        </div>
        <div className="search-filters">
          <details className="region-filter">
            <summary>지역 {regionIds.length > 0 && <b>{regionIds.length}</b>}</summary>
            <div className="region-groups">
              {regions.map((region) => (
                <div className="region-group" key={region.id}>
                  <label className="province"><input type="checkbox" checked={regionIds.includes(region.id)} onChange={() => toggleRegion(region)} />{region.name} 전체</label>
                  <div>{region.areas.map((area) => <label key={area.id}><input type="checkbox" checked={regionIds.includes(area.id)} onChange={() => toggleRegion(region, area)} />{area.name}</label>)}</div>
                </div>
              ))}
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
        </div>
        <p className="search-guide">검색 결과를 차례로 불러옵니다. 더 불러오기를 눌러 다음 공고를 계속 확인하세요.</p>
        <p className="search-guide">서비스 개선을 위해 검색 조건과 결과 건수를 기록합니다. 검색어에 개인정보를 입력하지 마세요.</p>
        {state.message && <div className={`notice ${state.status}`}>{state.message}</div>}
      </form>

      {result && (
        <section className="search-results">
          <div className="search-result-heading">
            <div><span className="eyebrow">SEARCH RESULTS</span><h2>통합 채용공고</h2></div>
            <strong>{result.items.length.toLocaleString()}개 불러옴</strong>
          </div>
          <div className="site-statuses">
            {result.sites.map((site) => <span className={site.status.toLowerCase()} key={site.source}><b>{site.sourceLabel}</b> {site.status === 'SUCCESS' ? `${site.returnedCount.toLocaleString()} / ${site.totalAvailable.toLocaleString()}개` : `${site.returnedCount.toLocaleString()}개 · 다시 시도 필요`}</span>)}
          </div>
          {visibleItems.length ? <div className="job-result-list">{visibleItems.map((item) => (
            <article className="job-result-card" key={`${item.source}-${item.postingId}`}>
              <div className="job-card-top">
                <span className={`source-chip ${item.source.toLowerCase()}`}>{item.sourceLabel}</span>
                <div className="job-card-dates"><span>{item.registeredDate || '등록일 미정'} ~ {item.deadline || '미정'}</span></div>
              </div>
              <p className="job-company">{item.companyName}</p>
              <div className="job-title-row">
                <h3><a href={item.url} target="_blank" rel="noreferrer">{item.title}</a></h3>
              </div>
              <div className="job-meta">{item.locations?.length > 0 && <span>📍 {item.locations.join(', ')}</span>}{item.career && <span>{item.career}</span>}{item.education && <span>{item.education}</span>}{item.employmentTypes?.length > 0 && <span>{item.employmentTypes.join(' · ')}</span>}</div>
              {item.skills?.length > 0 && <div className="skill-tags">{item.skills.slice(0, 8).map((skill) => <span key={skill}>{skill}</span>)}</div>}
            </article>
          ))}</div> : <p className="empty-search">{failed ? '공고를 불러오지 못했습니다. 다시 시도해 주세요.' : '조건에 맞는 공고를 찾지 못했습니다.'}</p>}
          {(totalPages > 1 || hasMore) && <div className="pagination">
            <button disabled={page === 1 || state.status === 'loading'} onClick={() => setPage((value) => value - 1)}>이전</button>
            <span>{page} / {totalPages}{hasMore ? ' · 더 불러올 수 있음' : ''}</span>
            <button disabled={state.status === 'loading' || (page === totalPages && !hasMore)} onClick={() => page < totalPages ? setPage((value) => value + 1) : loadMore()}>다음</button>
          </div>}
          <div className="load-more" aria-live="polite">
            {hasMore ? <button type="button" disabled={state.status === 'loading'} onClick={loadMore}>
              {state.status === 'loading' ? '공고 불러오는 중…' : failed ? '다시 불러오기' : '공고 더 불러오기'}
            </button> : <p>검색 결과를 모두 불러왔습니다.</p>}
          </div>
        </section>
      )}
    </div>
  )
}

export default JobSearch
