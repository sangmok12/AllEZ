import neighborhoods from './neighborhoods.json'

export default function NeighborhoodSelect({ regionId, value, onChange, ariaLabel = '읍·면·동' }) {
  const enabled = Boolean(regionId?.includes('_') || regionId === '1000')
  const items = neighborhoods.regions[regionId] || []
  const placeholder = !enabled ? '시·군·구 먼저 선택'
    : items.length ? '전체' : '선택 가능한 읍·면·동 없음'

  return <div className="neighborhood-field">
    <label>읍·면·동<select aria-label={ariaLabel} value={value} disabled={!enabled || !items.length}
      onChange={(event) => onChange(event.target.value, event.target.value ? event.target.selectedOptions[0].text : '')}>
      <option value="">{placeholder}</option>
      {items.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
    </select></label>
  </div>
}
