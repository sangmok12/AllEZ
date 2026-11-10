import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { createPortal } from 'react-dom'
import { mobilePlatform } from './homeScreenSupport'
import regions from './regions.json'
import neighborhoods from './neighborhoods.json'

const SHEET_QUERY = '(max-width: 620px)'
const sheetMode = () => Boolean(mobilePlatform(navigator)) || window.matchMedia(SHEET_QUERY).matches
const subscribeSheetMode = notify => {
  const media = window.matchMedia(SHEET_QUERY)
  media.addEventListener('change', notify)
  return () => media.removeEventListener('change', notify)
}

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
  const mobile = useSyncExternalStore(subscribeSheetMode, sheetMode, () => false)
  const [open, setOpen] = useState(false)
  const [portalHost, setPortalHost] = useState(null)
  const [provinceId, setProvinceId] = useState('')
  const [districtId, setDistrictId] = useState('')
  const province = regions.find(region => region.id === provinceId)
  useEffect(() => {
    const outside = event => {
      if (!sheetMode() && picker.current?.open && !picker.current.contains(event.target)) picker.current.open = false
    }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [])
  const close = () => {
    if (picker.current) picker.current.open = false
    setOpen(false)
    if (!mobile) picker.current?.querySelector('summary')?.focus()
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
  }, [provinceId, districtId, open, mobile])
  const selectionChips = selected.length > 0 && <div className="selected-region-chips">{selected.map(area => <button type="button" key={`${area.regionId}:${area.neighborhoodId || ''}`}
      aria-label={`${labels[area.regionId]} ${area.neighborhoodName || '전체'} 선택 해제`} onClick={() => toggle(area)}>
      {labels[area.regionId]} {area.neighborhoodName || '전체'} <span aria-hidden="true">×</span>
    </button>)}</div>
  const panel = (
    <div className="region-picker-panel" role="group" aria-label="지역 다중 선택">
      <div className="region-picker-top">
        <div className="region-picker-heading">
          <span className={mobile ? 'region-picker-title' : undefined}>{mobile ? '지역 선택' : `${selected.length} / 5곳 선택`}</span>
          <div><button type="button" onClick={() => onChange([])}>초기화</button><button type="button" onClick={close} aria-label="지역 선택 닫기" autoFocus={mobile}>닫기 ×</button></div>
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
      </div>
      {!mobile && province && <div className="region-picker-whole">
        {checkbox({ regionId: district?.id || province.id }, `${district?.name || province.name} 전체`)}
      </div>}
      <div className="region-picker-content" ref={content}>
        {mobile && province && <div className="region-picker-whole">
          {checkbox({ regionId: district?.id || province.id }, `${district?.name || province.name} 전체`)}
        </div>}
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
      <div className="region-picker-footer">
        {mobile && <p className="region-picker-selection-count">{selected.length} / 5곳 선택</p>}
        {mobile && selectionChips}
        <button className="region-picker-done" type="button" onClick={close}>선택 완료</button>
      </div>
    </div>
  )
  return <div className="part-time-region-list">
    <details className="part-time-region-picker" ref={picker}
      onToggle={event => {
        setOpen(event.currentTarget.open)
        if (event.currentTarget.open) {
          setPortalHost(event.currentTarget.closest('.app-shell') || document.body)
          browseProvince('')
          if (content.current) content.current.scrollTop = 0
        }
      }}
      onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); close() } }}>
      <summary>지역 선택 <svg className="part-time-region-arrow" aria-hidden="true" viewBox="0 0 16 16"><path d="m4 6 4 4 4-4" /></svg></summary>
      {!mobile && panel}
    </details>
    {mobile && open && createPortal(<MobileRegionSheet onClose={close}>{panel}</MobileRegionSheet>, portalHost || document.body)}
    {(!mobile || !open) && selectionChips}
  </div>
}

function MobileRegionSheet({ children, onClose }) {
  const dialog = useRef(null)
  useEffect(() => {
    const element = dialog.current
    const body = document.body
    const x = window.scrollX, y = window.scrollY
    const previous = { position: body.style.position, top: body.style.top, left: body.style.left, width: body.style.width, overflow: body.style.overflow }
    Object.assign(body.style, { position: 'fixed', top: `-${y}px`, left: `-${x}px`, width: '100%', overflow: 'hidden' })
    element.showModal()
    return () => {
      element.close()
      Object.assign(body.style, previous)
      window.scrollTo(x, y)
    }
  }, [])
  return <dialog className="region-picker-sheet" ref={dialog} aria-label="지역 선택" onClose={event => { if (!event.currentTarget.open) onClose() }}
    onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); onClose() } }}>
    {children}
  </dialog>
}

function checkedSame(a, b) { return a.regionId === b.regionId && (a.neighborhoodId || '') === (b.neighborhoodId || '') }
