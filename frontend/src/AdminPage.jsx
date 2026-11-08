import { useEffect, useRef, useState } from 'react'
import { apiUrl } from './api'
import regions from './regions.json'
import './AdminPage.css'
import { savedToken, saveToken, savedUsername, saveUsername, keepLoginPreference, saveKeepLoginPreference } from './adminPreferences'

const CATEGORIES = { all: '전체', jobs: '채용', part_time: '알바', homes: '부동산' }
const METRICS = [['visitors', '순방문자', '같은 브라우저 중복 제외'], ['visits', '방문 횟수', '30분 미활동 후 새 방문'], ['searches', '검색 실행', '추가조회·재시도 제외'], ['clicks', '공고 클릭', '원문을 연 횟수']]
const CAREERS = { NEWCOMER: '신입', EXPERIENCED: '경력', NO_PREFERENCE: '경력무관' }
const EDUCATIONS = { NO_PREFERENCE: '학력무관', HIGH_SCHOOL: '고졸↑', COLLEGE: '전문대졸↑', UNIVERSITY: '대졸↑', MASTER: '석사↑', DOCTOR: '박사' }
const EMPLOYMENTS = { PERMANENT: '정규직', CONTRACT: '계약직', INTERN: '인턴', DISPATCH: '파견', FREELANCE: '프리랜서', PART_TIME: '아르바이트' }
const regionNames = Object.fromEntries(regions.flatMap(r => [[r.id, r.name + ' 전체'], ...r.areas.map(a => [a.id, r.name + ' ' + a.name])]))

function today() { return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date()) }
function range(days) {
  const end = today()
  const start = new Date(end + 'T12:00:00Z')
  start.setUTCDate(start.getUTCDate() - days + 1)
  return { start: start.toISOString().slice(0, 10), end }
}


function timeLabel(value) { return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)) }
function conditionText(c = {}, neighborhoodNames = {}) {
  const multi = c.locations?.length > 0
  const parts = [multi ? c.locations.map(area => [regionNames[area.region_id] || area.region_id,
    area.neighborhood_name || (area.neighborhood_id ? '읍·면·동 이름 미확인' : '')].filter(Boolean).join(' ')).join(', ')
    : (c.region_ids || []).map(id => regionNames[id] || id).join(', ') || '지역 전체']
  if (!multi && c.neighborhood_id) parts.push(c.neighborhood_name || neighborhoodNames[c.neighborhood_id] || '읍·면·동 이름 미확인')
  if (CAREERS[c.career_type]) parts.push(CAREERS[c.career_type])
  if (c.minimum_career_years != null || c.maximum_career_years != null) parts.push(`${c.minimum_career_years ?? 0}~${c.maximum_career_years ?? '제한 없음'}년`)
  if (EDUCATIONS[c.minimum_education]) parts.push(EDUCATIONS[c.minimum_education])
  if (c.employment_types?.length) parts.push(c.employment_types.map(t => EMPLOYMENTS[t] || t).join('·'))
  return parts.join(' / ')
}

export default function AdminPage() {
  const initial = range(7)
  const [token, setToken] = useState(savedToken)
  const [username, setUsername] = useState(savedUsername)
  const [rememberId, setRememberId] = useState(() => Boolean(savedUsername()))
  const [rememberMe, setRememberMe] = useState(keepLoginPreference)
  const [password, setPassword] = useState('')
  const [loginBusy, setLoginBusy] = useState(false)
  const [error, setError] = useState('')
  const [draft, setDraft] = useState({ ...initial, category: 'all' })
  const [filters, setFilters] = useState({ ...initial, category: 'all', page: 1 })
  const [report, setReport] = useState(null)
  const [neighborhoodNames, setNeighborhoodNames] = useState({})

  useEffect(() => {
    const ids = [...new Set((report?.history || []).filter(row => row.search_criteria?.neighborhood_id && !row.search_criteria.neighborhood_name)
      .map(row => row.search_criteria.region_ids?.[0]).filter(id => typeof id === 'string' && /^[A-Z0-9]{4}(?:_[A-Z0-9]{4})?$/.test(id)))]
    if (!ids.length) return
    const controller = new AbortController()
    let active = true
    Promise.allSettled(ids.map(async id => {
      const response = await fetch(apiUrl(`/api/part-time-search/neighborhoods?regionId=${encodeURIComponent(id)}`), { signal: controller.signal })
      if (!response.ok) throw new Error('lookup failed')
      const items = await response.json()
      if (!Array.isArray(items)) throw new Error('invalid lookup')
      return items
    })).then(results => {
      if (!active) return
      const found = Object.fromEntries(results.filter(result => result.status === 'fulfilled').flatMap(result => result.value.map(item => [item.id, item.name])))
      setNeighborhoodNames(current => ({ ...current, ...found }))
    })
    return () => { active = false; controller.abort() }
  }, [report])
  const [busy, setBusy] = useState(false)
  const [updatedAt, setUpdatedAt] = useState('')
  const requestId = useRef(0)

  useEffect(() => {
    document.title = 'AllEZ · 관리자 통계'
    const meta = document.createElement('meta')
    meta.name = 'robots'; meta.content = 'noindex, nofollow'
    document.head.appendChild(meta)
    return () => meta.remove()
  }, [])

  useEffect(() => {
    if (!token) return
    const controller = new AbortController()
    const current = ++requestId.current
    async function load() {
      setBusy(true); setError(''); setReport(null)
      try {
        let response
        for (let attempt = 0; attempt < 2; attempt++) {
          response = await fetch(apiUrl('/api/admin/report?' + new URLSearchParams(filters)), {
            headers: { Authorization: 'Bearer ' + token }, cache: 'no-store',
            signal: AbortSignal.any([controller.signal, AbortSignal.timeout(150000)]),
          })
          if (response.status < 500 || attempt === 1) break
        }
        if (response.status === 401) {
          saveToken(''); setToken(''); setReport(null)
          throw new Error('로그인이 만료됐습니다. 다시 로그인해 주세요.')
        }
        const body = await response.json()
        if (!response.ok) throw new Error(body.detail || '통계를 불러오지 못했습니다.')
        if (current === requestId.current) { setReport(body); setUpdatedAt(new Date().toISOString()) }
      } catch (e) {
        if (!controller.signal.aborted && current === requestId.current) setError(e.message || '연결을 확인해 주세요.')
      } finally {
        if (!controller.signal.aborted && current === requestId.current) setBusy(false)
      }
    }
    void load()
    return () => controller.abort()
  }, [token, filters])

  async function login(event) {
    event.preventDefault(); setError(''); setLoginBusy(true)
    try {
      const response = await fetch(apiUrl('/api/admin/login'), {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, rememberMe }), signal: AbortSignal.timeout(150000),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.detail || '아이디 또는 비밀번호를 확인해 주세요.')
      saveUsername(rememberId ? username : '')
      saveKeepLoginPreference(rememberMe)
      saveToken(body.token, rememberMe, body.expiresIn); setToken(body.token)
    } catch (e) { setError(e.message || '로그인 서버 연결을 확인해 주세요.') }
    finally { setPassword(''); setLoginBusy(false) }
  }

  function logout() {
    void fetch(apiUrl('/api/admin/logout'), { method: 'POST', headers: { Authorization: 'Bearer ' + token }, keepalive: true }).catch(() => {})
    saveToken(''); setToken(''); setReport(null); setError('')
  }

  function apply(event) {
    event.preventDefault()
    const diff = (Date.parse(draft.end) - Date.parse(draft.start)) / 86400000
    if (!draft.start || !draft.end || diff < 0 || diff > 365) return setError('시작일과 종료일을 확인해 주세요. 최대 366일까지 조회할 수 있습니다.')
    setFilters({ ...draft, page: 1 })
  }

  function preset(days) {
    const next = { ...range(days), category: draft.category }
    setDraft(next); setFilters({ ...next, page: 1 })
  }

  const pages = Math.max(1, Math.ceil((report?.historyTotal || 0) / 25))
  const maxDaily = Math.max(1, ...(report?.daily || []).flatMap(d => [d.visitors, d.searches, d.clicks]))

  return <div className="admin-shell">
    <header className="admin-header">
      <a className="brand" href="/"><span className="brand-wordmark"><span className="brand-all">All</span><span className="brand-ez">EZ</span></span></a>
      <span>관리자 통계</span>{token && <button onClick={logout}>로그아웃</button>}
    </header>
    {!token ? <main className="admin-login">
      <span className="eyebrow">ADMIN</span><h1>관리자 로그인</h1><p>방문과 검색 현황을 확인하세요.</p>
      <form onSubmit={login}>
        <label>아이디<input autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} required /></label>
        <label>비밀번호<input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required /></label>
        <div className="admin-login-options">
          <label className="admin-login-option"><input type="checkbox" checked={rememberId} onChange={e => { setRememberId(e.target.checked); if (!e.target.checked) saveUsername('') }} />아이디 저장</label>
          <label className="admin-login-option"><input type="checkbox" checked={rememberMe} onChange={e => setRememberMe(e.target.checked)} />로그인 상태 유지</label>
        </div>
        <p className="admin-login-hint">로그인 상태 유지 선택 시 이 브라우저에서 최대 30일간 유지됩니다.</p>
        {error && <p className="admin-error" role="alert">{error}</p>}
        <button className="admin-primary" disabled={loginBusy}>{loginBusy ? '로그인 중…' : '로그인'}</button>
      </form>
    </main> : <main className="admin-content">
      <div className="admin-title"><div><span className="eyebrow">OVERVIEW</span><h1>서비스 이용 현황</h1><p>분야별 방문과 검색 흐름을 한눈에 확인하세요.</p></div><span className="admin-updated">{updatedAt && timeLabel(updatedAt) + ' 갱신'}</span></div>
      <form className="admin-filters" onSubmit={apply}>
        <div className="admin-presets">{[[1,'오늘'],[7,'최근 7일'],[30,'최근 30일']].map(([days,label]) => <button type="button" key={days} onClick={() => preset(days)}>{label}</button>)}</div>
        <label>시작일<input type="date" value={draft.start} onChange={e => setDraft({ ...draft, start: e.target.value })} required /></label>
        <label>종료일<input type="date" value={draft.end} onChange={e => setDraft({ ...draft, end: e.target.value })} required /></label>
        <label>분야<select value={draft.category} onChange={e => setDraft({ ...draft, category: e.target.value })}>{Object.entries(CATEGORIES).map(([key,name]) => <option key={key} value={key}>{name}</option>)}</select></label>
        <button className="admin-primary" disabled={busy}>조회</button>
      </form>
      {error && <p className="admin-error" role="alert">{error}</p>}
      {busy && <p className="admin-loading" role="status">통계를 불러오는 중입니다. 서버 준비에 시간이 걸릴 수 있습니다.</p>}
      {report && <div aria-busy={busy} className={busy ? 'admin-report refreshing' : 'admin-report'}>
        <div className="admin-metrics">{METRICS.map(([key,label,hint]) => <section className="admin-metric" key={key}><span>{label}</span><strong>{Number(report.totals[key]).toLocaleString()}</strong><small>{hint}</small></section>)}</div>
        <p className="admin-note">한국 시간 기준 · 순방문자는 브라우저 기준 추정치입니다. 전체는 분야 간 중복을 제거하므로 분야별 합계와 다를 수 있습니다.</p>
        <section className="admin-panel"><h2>분야별 현황</h2><div className="admin-table-scroll"><table><thead><tr><th>분야</th>{METRICS.map(([key,label]) => <th key={key}>{label}</th>)}</tr></thead><tbody>{report.categories.slice().sort((a, b) => ['jobs','part_time','homes'].indexOf(a.category) - ['jobs','part_time','homes'].indexOf(b.category)).filter(c => filters.category === 'all' || c.category === filters.category).map(c => <tr key={c.category}><th>{CATEGORIES[c.category]}</th>{METRICS.map(([key]) => <td key={key}>{Number(c[key]).toLocaleString()}</td>)}</tr>)}</tbody></table></div></section>
        <section className="admin-panel"><div className="admin-panel-heading"><h2>일별 추이</h2><div className="admin-legend"><span>방문자</span><span>검색</span><span>공고 클릭</span></div></div>
          <div className="admin-chart">{report.daily.map(day => <div className="admin-day" key={day.day}><div className="admin-bars">{['visitors','searches','clicks'].map(key => <div key={key} className={'admin-bar ' + key} style={{ height: (Number(day[key]) / maxDaily * 100) + '%' }} title={day.day + ' ' + key + ': ' + day[key]} />)}</div><small>{day.day.slice(5)}</small></div>)}</div>
          <details className="admin-daily-details"><summary>일별 수치 보기</summary><div className="admin-table-scroll"><table><thead><tr><th>날짜</th>{METRICS.map(([key,label]) => <th key={key}>{label}</th>)}</tr></thead><tbody>{report.daily.map(day => <tr key={day.day}><th>{day.day}</th>{METRICS.map(([key]) => <td key={key}>{day[key]}</td>)}</tr>)}</tbody></table></div></details>
        </section>
        <section className="admin-panel"><div className="admin-panel-heading"><h2>검색 기록</h2><span>{Number(report.historyTotal).toLocaleString()}건</span></div><p className="admin-note">결과 건수는 첫 묶음 기준입니다. 이전 검색 기록도 포함되어 요약의 검색 실행 수와 다를 수 있습니다.</p>
          <div className="admin-table-scroll"><table className="admin-history"><thead><tr><th>검색 시각</th><th>분야</th><th>검색어·조건</th><th>결과</th><th>상태</th></tr></thead><tbody>{report.history.map(row => <tr key={row.event_id}><td>{timeLabel(row.occurred_at)}</td><td>{CATEGORIES[row.category]}</td><td><b>{row.search_criteria?.keyword || '검색어 없음'}</b><small>{conditionText(row.search_criteria, neighborhoodNames)}</small></td><td>{row.returned_count == null ? '—' : row.returned_count + '개'}</td><td><span className={'admin-outcome ' + (row.outcome || 'pending')}>{{ success:'성공', partial_failure:'일부 실패', failure:'실패' }[row.outcome] || (row.category === 'part_time' ? '결과 기록 없음' : '결과 미수신')}</span></td></tr>)}</tbody></table></div>
          {!report.history.length && <p className="admin-empty">이 기간의 검색 기록이 없습니다.</p>}
          <div className="admin-pagination"><button disabled={filters.page <= 1 || busy} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>이전</button><span>{filters.page} / {pages}</span><button disabled={filters.page >= pages || busy} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>다음</button></div>
        </section>
      </div>}
    </main>}
  </div>
}
