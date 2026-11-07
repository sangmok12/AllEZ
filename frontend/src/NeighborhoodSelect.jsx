import { useEffect, useState } from 'react'
import { apiUrl } from './api'

export default function NeighborhoodSelect({ regionId, value, onChange }) {
  const enabled = Boolean(regionId?.includes('_') || regionId === '1000')
  const [lookup, setLookup] = useState({ status: 'loading', items: [] })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!enabled) return
    const controller = new AbortController()
    let active = true
    fetch(apiUrl(`/api/part-time-search/neighborhoods?regionId=${encodeURIComponent(regionId)}`), {
      signal: controller.signal,
    }).then(async (response) => {
      const items = await response.json()
      if (!response.ok || !Array.isArray(items)) throw new Error('lookup failed')
      if (active) setLookup({ status: 'success', items })
    }).catch(() => {
      if (active) setLookup({ status: 'error', items: [] })
    })
    return () => { active = false; controller.abort() }
  }, [regionId, enabled, attempt])

  const placeholder = !enabled ? '시·군·구 먼저 선택'
    : lookup.status === 'loading' ? '불러오는 중…'
      : lookup.status === 'error' ? '목록 불러오기 실패'
        : lookup.items.length ? '전체' : '선택 가능한 읍·면·동 없음'

  return <div className="neighborhood-field">
    <label>읍·면·동<select aria-label="읍·면·동" value={value} disabled={!enabled || lookup.status !== 'success' || !lookup.items.length}
      onChange={(event) => onChange(event.target.value, event.target.value ? event.target.selectedOptions[0].text : '')}>
      <option value="">{placeholder}</option>
      {lookup.items.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
    </select></label>
    {enabled && lookup.status === 'error' && <div className="neighborhood-error" role="status">
      <span>동네 목록을 불러오지 못했습니다.</span>
      <button type="button" onClick={() => {
        setLookup({ status: 'loading', items: [] })
        setAttempt((current) => current + 1)
      }}>다시 시도</button>
    </div>}
  </div>
}
