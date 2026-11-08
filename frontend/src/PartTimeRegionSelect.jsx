import regions from './regions.json'
import NeighborhoodSelect from './NeighborhoodSelect'
import { emptyLocation } from './partTimeLocations'

export default function PartTimeRegionSelect({ value, onChange }) {
  const update = (index, location) => onChange(value.map((area, i) => i === index ? location : area))
  return <div className="part-time-region-list">
    {value.map((area, index) => {
      const province = regions.find(region => region.id === area.regionId || region.areas.some(item => item.id === area.regionId))
      const suffix = index ? ` ${index + 1}` : ''
      return <div className="part-time-region-row" key={index}>
        {value.length > 1 && <div className="region-row-heading"><span>지역 {index + 1}</span>
          <button type="button" aria-label={`지역 ${index + 1} 삭제`} onClick={() => onChange(value.filter((_, i) => i !== index))}>삭제</button>
        </div>}
        <label>시·도<select aria-label={`시·도${suffix}`} value={province?.id || ''}
          onChange={event => update(index, { ...emptyLocation(), regionId: event.target.value })}>
          <option value="">지역 선택</option>{regions.map(region => <option key={region.id} value={region.id}>{region.name}</option>)}
        </select></label>
        <label>시·군·구<select aria-label={`시·군·구${suffix}`} value={area.regionId || ''} disabled={!province?.areas.length}
          onChange={event => update(index, { ...emptyLocation(), regionId: event.target.value })}>
          <option value={province?.id || ''}>{province ? '전체' : '시·도 먼저 선택'}</option>
          {province?.areas.map(district => <option key={district.id} value={district.id}>{district.name}</option>)}
        </select></label>
        <NeighborhoodSelect key={area.regionId || 'nationwide'} regionId={area.regionId} value={area.neighborhoodId}
          ariaLabel={`읍·면·동${suffix}`} onChange={(id, name) => update(index, { ...area, neighborhoodId: id, neighborhoodName: name })} />
      </div>
    })}
    <button type="button" className="add-search-region" disabled={value.length >= 10}
      onClick={() => onChange([...value, emptyLocation()])}>+ 지역 추가{value.length >= 10 ? ' (최대 10곳)' : ''}</button>
  </div>
}
