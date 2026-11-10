import { useEffect, useRef, useState } from 'react'
import regions from './regions.json'
import neighborhoods from './neighborhoods.json'

const labels = Object.fromEntries(regions.flatMap(region => [[region.id, region.name],
  ...region.areas.map(area => [area.id, `${region.name} ${area.name}`])]))
const covers = (a, b) => a.neighborhoodId
  ? a.regionId === b.regionId && a.neighborhoodId === b.neighborhoodId
  : a.regionId === b.regionId || b.regionId.startsWith(a.regionId + '_')

export default function PartTimeRegionSelect({ value, onChange }) {
  const picker = useRef(null)
  const content = useRef(null)
  const stepHeading = useRef(null)
  const selected = value.filter(area => area.regionId)
  const [provinceId, setProvinceId] = useState('')
  const [districtId, setDistrictId] = useState('')
  const province = regions.find(region => region.id === provinceId)
  useEffect(() => {
    const outside = event => {
      if (picker.current?.open && !picker.current.contains(event.target)) picker.current.open = false
    }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [])
  const close = () => {
    picker.current.open = false
    picker.current.querySelector('summary')?.focus()
  }
  const browseProvince = id => { setProvinceId(id); setDistrictId('') }
  const checked = area => selected.some(item => item.regionId === area.regionId && (item.neighborhoodId || '') === (area.neighborhoodId || ''))
  const toggle = area => {
    if (checked(area)) onChange(selected.filter(item => !checkedSame(item, area)))
    else {
      const remaining = selected.filter(item => !covers(item, area) && !covers(area, item))
      if (remaining.length < 5) onChange([...remaining, area])
    }
  }
  const checkbox = (area, name) => <label className="region-check" key={area.neighborhoodId || area.regionId}>
    <input type="checkbox" checked={checked(area)} disabled={!checked(area) && selected.filter(item => !covers(item, area) && !covers(area, item)).length >= 5}
      onChange={() => toggle(area)} /><span>{name}</span>
  </label>
  const district = province?.areas.find(area => area.id === districtId)
  const dongRegion = districtId || (province && !province.areas.length ? province.id : '')
  const dongs = neighborhoods.regions[dongRegion] || []
  const step = !province ? '시·도' : dongRegion ? '읍·면·동' : '시·군·구'
  useEffect(() => {
    if (content.current) content.current.scrollTop = 0
    if (picker.current?.open) stepHeading.current?.focus()
  }, [provinceId, districtId])
  return <div className="part-time-region-list">
    <details className="part-time-region-picker" ref={picker}
      onToggle={event => {
        if (event.currentTarget.open) {
          browseProvince('')
          if (content.current) content.current.scrollTop = 0
        }
      }}
      onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); close() } }}>
      <summary>지역 선택 <svg className="part-time-region-arrow" aria-hidden="true" viewBox="0 0 16 16"><path d="m4 6 4 4 4-4" /></svg></summary>
      <div className="region-picker-panel" role="group" aria-label="지역 다중 선택">
        <div className="region-picker-heading">
          <span>{selected.length} / 5곳 선택</span>
          <div><button type="button" onClick={() => onChange([])}>초기화</button><button type="button" onClick={close} aria-label="지역 선택 닫기">닫기 ×</button></div>
        </div>
        <nav className="region-picker-breadcrumb" aria-label="지역 탐색 경로">
          <button type="button" disabled={!province} aria-current={!province ? 'step' : undefined} onClick={() => browseProvince('')}>지역</button>
          {province && <><span aria-hidden="true">›</span><button type="button" aria-current={!district ? 'step' : undefined} onClick={() => setDistrictId('')}>{province.name}</button></>}
          {district && <><span aria-hidden="true">›</span><span aria-current="step">{district.name}</span></>}
        </nav>
        {province && <button className="region-picker-back" type="button" onClick={() => district ? setDistrictId('') : browseProvince('')}>
          ← {district ? '시·군·구' : '시·도'} 다시 선택
        </button>}
        <p className="region-picker-step" aria-live="polite" tabIndex={-1} ref={stepHeading}>{step} 선택</p>
        {province && <div className="region-picker-whole">
          {checkbox({ regionId: district?.id || province.id }, `${district?.name || province.name} 전체`)}
        </div>}
        <div className="region-picker-content" ref={content}>
          {!province && <div className="region-browse-list" role="group" aria-label="시·도 목록">
            {regions.map(region => <button type="button" key={region.id} onClick={() => browseProvince(region.id)}><span>{region.name}</span><span aria-hidden="true">›</span></button>)}
          </div>}
          {province && !dongRegion && <div className="region-browse-list" role="group" aria-label="시·군·구 목록">
            {province.areas.map(area => <button type="button" key={area.id} onClick={() => setDistrictId(area.id)}><span>{area.name}</span><span aria-hidden="true">›</span></button>)}
          </div>}
          {dongRegion && <div className="region-check-list" role="group" aria-label="읍·면·동 목록">
            {dongs.map(dong => checkbox({ regionId: dongRegion, neighborhoodId: dong.id, neighborhoodName: dong.name }, dong.name))}
            {!dongs.length && <p>이 지역은 전체로 선택할 수 있어요.</p>}
          </div>}
        </div>
        <button className="region-picker-done" type="button" onClick={close}>선택 완료</button>
      </div>
    </details>
    {selected.length > 0 && <div className="selected-region-chips">{selected.map(area => <button type="button" key={`${area.regionId}:${area.neighborhoodId || ''}`}
      aria-label={`${labels[area.regionId]} ${area.neighborhoodName || '전체'} 선택 해제`} onClick={() => toggle(area)}>
      {labels[area.regionId]} {area.neighborhoodName || '전체'} <span aria-hidden="true">×</span>
    </button>)}</div>}
  </div>
}

function checkedSame(a, b) { return a.regionId === b.regionId && (a.neighborhoodId || '') === (b.neighborhoodId || '') }
