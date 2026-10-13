import { useEffect, useMemo, useState } from 'react'
import './JobSearch.css'
import { apiUrl } from './api'

const CAREERS = [['ANY', '경력 전체'], ['NEWCOMER', '신입'], ['EXPERIENCED', '경력'], ['NO_PREFERENCE', '경력무관']]
const EDUCATIONS = [['ANY', '학력 전체'], ['NO_PREFERENCE', '학력무관'], ['HIGH_SCHOOL', '고졸 이상'], ['COLLEGE', '전문대졸 이상'], ['UNIVERSITY', '대졸 이상'], ['MASTER', '석사 이상'], ['DOCTOR', '박사']]
const EMPLOYMENTS = [['PERMANENT', '정규직'], ['CONTRACT', '계약직'], ['INTERN', '인턴'], ['DISPATCH', '파견직'], ['FREELANCE', '프리랜서'], ['PART_TIME', '아르바이트']]
const PAGE_SIZE = 25

function JobSearch({ onAnalyze, onLoadingChange }) {
  const [regions, setRegions] = useState([])
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

  useEffect(() => {
    fetch(apiUrl('/api/job-search/regions')).then((response) => {
      if (!response.ok) throw new Error()
      return response.json()
    }).then(setRegions).catch(() => setState({ status: 'error', message: '지역 정보를 불러오지 못했습니다.' }))
  }, [])

  useEffect(() => onLoadingChange(state.status === 'loading'), [state.status, onLoadingChange])

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

  const submit = async (event) => {
    event.preventDefault()
    if (!keyword.trim()) return setState({ status: 'error', message: '검색어를 입력해 주세요.' })
    if (minimumCareerYears && maximumCareerYears && Number(minimumCareerYears) > Number(maximumCareerYears)) {
      return setState({ status: 'error', message: '최소 경력은 최대 경력보다 클 수 없습니다.' })
    }
    setState({ status: 'loading', message: '' })
    setResult(null)
    try {
      const response = await fetch(apiUrl('/api/job-search'), {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          keyword: keyword.trim(), regionIds, careerType,
          minimumCareerYears: minimumCareerYears === '' ? null : Number(minimumCareerYears),
          maximumCareerYears: maximumCareerYears === '' ? null : Number(maximumCareerYears),
          minimumEducation, employmentTypes,
        }),
      })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.detail || '검색 요청을 처리하지 못했습니다.')
      setResult(data)
      setPage(1)
      setState({ status: 'success', message: `${data.totalReturned.toLocaleString()}개의 공고를 모았습니다.` })
    } catch (error) {
      setState({ status: 'error', message: error.message || '검색 중 오류가 발생했습니다.' })
    }
  }

  return (
    <div className="job-search-page">
      <section className="search-intro">
        <span className="eyebrow">INTEGRATED JOB SEARCH</span>
        <h1>두 채용사이트의 공고를<br /><strong>한 번에</strong> 찾아보세요.</h1>
        <p>같은 조건으로 잡코리아와 사람인을 검색하고, 원하는 공고를 바로 JobFit 분석으로 연결합니다.</p>
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
        <p className="search-limit-guide">최신 등록순으로 사이트별 최대 250개, 전체 최대 500개까지 가져옵니다.</p>
        {state.message && <div className={`notice ${state.status}`}>{state.message}</div>}
      </form>

      {result && (
        <section className="search-results">
          <div className="search-result-heading">
            <div><span className="eyebrow">SEARCH RESULTS</span><h2>통합 채용공고</h2></div>
            <strong>{result.totalReturned.toLocaleString()}개</strong>
          </div>
          <div className="site-statuses">
            {result.sites.map((site) => <span className={site.status.toLowerCase()} key={site.source}><b>{site.sourceLabel}</b> {site.status === 'SUCCESS' ? `${site.returnedCount.toLocaleString()}개` : '검색 실패'}{site.limited && ' · 상한 적용'}</span>)}
          </div>
          {result.limited && <p className="limit-notice">검색 결과가 많아 최신 공고 500개만 표시합니다. 조건을 더 구체적으로 선택하면 정확하게 찾을 수 있습니다.</p>}
          {visibleItems.length ? <div className="job-result-list">{visibleItems.map((item) => (
            <article className="job-result-card" key={`${item.source}-${item.postingId}`}>
              <div className="job-card-top">
                <span className={`source-chip ${item.source.toLowerCase()}`}>{item.sourceLabel}</span>
                <div className="job-card-dates"><span>{item.registeredDate || '등록일 미정'} ~ {item.deadline || '미정'}</span></div>
              </div>
              <p className="job-company">{item.companyName}</p>
              <div className="job-title-row">
                <h3><a href={item.url} target="_blank" rel="noreferrer">{item.title}</a></h3>
                <button type="button" onClick={() => onAnalyze(item.url)}>JobFit 분석</button>
              </div>
              <div className="job-meta">{item.locations?.length > 0 && <span>📍 {item.locations.join(', ')}</span>}{item.career && <span>{item.career}</span>}{item.education && <span>{item.education}</span>}{item.employmentTypes?.length > 0 && <span>{item.employmentTypes.join(' · ')}</span>}</div>
              {item.skills?.length > 0 && <div className="skill-tags">{item.skills.slice(0, 8).map((skill) => <span key={skill}>{skill}</span>)}</div>}
            </article>
          ))}</div> : <p className="empty-search">조건에 맞는 공고를 찾지 못했습니다.</p>}
          {totalPages > 1 && <div className="pagination"><button disabled={page === 1} onClick={() => setPage((value) => value - 1)}>이전</button><span>{page} / {totalPages}</span><button disabled={page === totalPages} onClick={() => setPage((value) => value + 1)}>다음</button></div>}
        </section>
      )}
    </div>
  )
}

export default JobSearch
