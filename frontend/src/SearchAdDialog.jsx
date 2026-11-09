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
    <dialog className="search-ad-dialog" ref={dialog} aria-labelledby="search-ad-title" onClose={(event) => { if (!event.currentTarget.open) onClose() }}>
      <div className="search-ad-heading">
        <h2 id="search-ad-title">쿠팡 추천 상품</h2>
        <button type="button" onClick={() => dialog.current.close()} aria-label="광고 닫기">닫기 ×</button>
      </div>
      <p className="search-ad-description">광고를 누르면 쿠팡으로 이동합니다. 검색은 광고 클릭과 관계없이 진행됩니다.</p>
      <CoupangBanner />
      <button className="search-ad-dismiss" type="button" onClick={() => dialog.current.close()}>검색으로 돌아가기</button>
    </dialog>,
    document.body,
  )
}
