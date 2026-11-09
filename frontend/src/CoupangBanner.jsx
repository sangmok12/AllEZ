import { useEffect, useRef, useState } from 'react'
import { recordAdClick } from './adCooldown'
import './CoupangBanner.css'

const DESKTOP = { id: 1048386, width: 728, height: 90 }
const MOBILE = { id: 1048387, width: 320, height: 100 }

function bannerDocument(banner) {
  const options = { ...banner, template: 'carousel', trackingCode: 'AF2181527', subId: 'AllEZSearch', width: String(banner.width), height: String(banner.height), tsource: '' }
  // The official script writes its banner into the document. Keep it out of React's document.
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body{margin:0;padding:0;overflow:hidden}iframe{display:block;border:0}</style></head><body><script src="https://ads-partners.coupang.com/g.js"></script><script>const options=${JSON.stringify(options)};options.onClicked=function(){parent.postMessage({type:"allez:coupang-ad-clicked",bannerId:options.id},${JSON.stringify(window.location.origin)});};new PartnersCoupang.G(options);</script></body></html>`
}

const DOCUMENTS = { [DESKTOP.id]: bannerDocument(DESKTOP), [MOBILE.id]: bannerDocument(MOBILE) }

export default function CoupangBanner() {
  const container = useRef(null)
  const frame = useRef(null)
  const [availableWidth, setAvailableWidth] = useState(0)

  useEffect(() => {
    const observer = new ResizeObserver(([entry]) => setAvailableWidth(entry.contentRect.width))
    observer.observe(container.current)
    return () => observer.disconnect()
  }, [])

  const banner = availableWidth >= DESKTOP.width ? DESKTOP : MOBILE
  const scale = Math.min(1, availableWidth / banner.width)

  useEffect(() => {
    const clicked = (event) => {
      if (event.origin !== window.location.origin || event.source !== frame.current?.contentWindow) return
      if (event.data?.type === 'allez:coupang-ad-clicked' && event.data.bannerId === banner.id) recordAdClick()
    }
    window.addEventListener('message', clicked)
    return () => window.removeEventListener('message', clicked)
  }, [banner.id])

  return (
    <aside className="coupang-ad" aria-label="쿠팡 파트너스 광고" ref={container}>
      <span className="coupang-ad-label">광고 · 쿠팡 파트너스</span>
      {availableWidth > 0 && <div className="coupang-ad-frame" style={{ width: banner.width * scale, height: banner.height * scale }}>
        <iframe
          ref={frame}
          key={banner.id}
          title="쿠팡 추천 상품 광고"
          srcDoc={DOCUMENTS[banner.id]}
          width={banner.width}
          height={banner.height}
          style={{ transform: `scale(${scale})` }}
        />
      </div>}
      <p className="coupang-ad-disclosure">이 게시물은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.</p>
    </aside>
  )
}
