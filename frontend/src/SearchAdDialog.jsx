import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import CoupangBanner from './CoupangBanner'
import './SearchAdDialog.css'

export default function SearchAdDialog({ onClose }) {
  const dialog = useRef(null)

  useEffect(() => {
    const element = dialog.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element.showModal()
    return () => {
      element.close()
      document.body.style.overflow = previousOverflow
    }
  }, [])

  return createPortal(
    <dialog className="search-ad-dialog" ref={dialog} aria-label="쿠팡 파트너스 광고" onClose={(event) => { if (!event.currentTarget.open) onClose() }}>
      <div className="search-ad-heading">
        <button type="button" onClick={() => dialog.current.close()} aria-label="광고 닫기">닫기 ×</button>
      </div>
      <p className="search-ad-benefit">광고를 1회 클릭하면 이 브라우저에서 1시간 동안 광고가 표시되지 않습니다.</p>
      <CoupangBanner />
      <button className="search-ad-dismiss" type="button" onClick={() => dialog.current.close()}>무시하고 검색</button>
    </dialog>,
    document.body,
  )
}
