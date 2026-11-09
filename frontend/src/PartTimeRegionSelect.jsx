import { useRef, useState } from 'react'
import regions from './regions.json'
import neighborhoods from './neighborhoods.json'

const labels = Object.fromEntries(regions.flatMap(region => [[region.id, region.name],
  ...region.areas.map(area => [area.id, `${region.name} ${area.name}`])]))
const covers = (a, b) => a.neighborhoodId
  ? a.regionId === b.regionId && a.neighborhoodId === b.neighborhoodId
  : a.regionId === b.regionId || b.regionId.startsWith(a.regionId + '_')

export default function PartTimeRegionSelect({ value, onChange }) {
  const picker = useRef(null)
  const selected = value.filter(area => area.regionId)
  const [provinceId, setProvinceId] = useState('')
  const [districtId, setDistrictId] = useState('')
  const province = regions.find(region => region.id === provinceId)
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
  const dongRegion = districtId || (provinceId === '1000' ? '1000' : '')
  return <div className="part-time-region-list">
    <details className="part-time-region-picker" ref={picker}>
      <summary>지역 선택 <svg className="part-time-region-arrow" aria-hidden="true" viewBox="0 0 16 16"><path d="m4 6 4 4 4-4" /></svg></summary>
      <div className="region-picker-panel" role="group" aria-label="지역 다중 선택">
        <div className="region-picker-heading"><span>최대 5곳 선택</span><button type="button" onClick={() => onChange([])}>초기화</button></div>
        <div className="region-picker-nav">
          <label>시·도<select aria-label="지역 탐색 시·도" value={provinceId} onChange={event => { setProvinceId(event.target.value); setDistrictId('') }}>
            <option value="">시·도 선택</option>{regions.map(region => <option key={region.id} value={region.id}>{region.name}</option>)}
          </select></label>
          <label>시·군·구<select aria-label="지역 탐색 시·군·구" value={districtId} disabled={!province?.areas.length} onChange={event => setDistrictId(event.target.value)}>
            <option value="">시·군·구 선택</option>{province?.areas.map(area => <option key={area.id} value={area.id}>{area.name}</option>)}
          </select></label>
        </div>
        <div className="region-check-list">
          {!province && <p>시·도를 선택해 주세요.</p>}
          {province && !district && checkbox({ regionId: province.id }, `${province.name} 전체`)}
          {district && checkbox({ regionId: district.id }, `${district.name} 전체`)}
          {province && !dongRegion && province.areas.map(area => checkbox({ regionId: area.id }, area.name))}
          {dongRegion && (neighborhoods.regions[dongRegion] || []).map(dong => checkbox({ regionId: dongRegion, neighborhoodId: dong.id, neighborhoodName: dong.name }, dong.name))}
        </div>
        <button className="region-picker-done" type="button" onClick={() => { picker.current.open = false }}>선택 완료</button>
      </div>
    </details>
    {selected.length > 0 && <div className="selected-region-chips">{selected.map(area => <button type="button" key={`${area.regionId}:${area.neighborhoodId || ''}`}
      aria-label={`${labels[area.regionId]} ${area.neighborhoodName || '전체'} 선택 해제`} onClick={() => toggle(area)}>
      {labels[area.regionId]} {area.neighborhoodName || '전체'} <span aria-hidden="true">×</span>
    </button>)}</div>}
  </div>
}

function checkedSame(a, b) { return a.regionId === b.regionId && (a.neighborhoodId || '') === (b.neighborhoodId || '') }
