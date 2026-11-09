import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { HOME_HINT_KEY, MOBILE_SITE_URL, isStandalone, mobilePlatform } from './homeScreenSupport'
import './MobileAccess.css'

const AccessContext = createContext(null)

function hintDismissed() {
  try { return window.localStorage.getItem(HOME_HINT_KEY) === '1' } catch { return false }
}

export function MobileAccessProvider({ children }) {
  const platform = mobilePlatform(navigator)
  const [dismissed, setDismissed] = useState(hintDismissed)
  const [standalone, setStandalone] = useState(() => isStandalone(navigator, window.matchMedia('(display-mode: standalone)').matches))
  const [installEvent, setInstallEvent] = useState(null)
  const [panel, setPanel] = useState(null)

  const dismissHint = () => {
    setDismissed(true)
    try { window.localStorage.setItem(HOME_HINT_KEY, '1') } catch { /* Dismiss for this visit if storage is unavailable. */ }
  }

  useEffect(() => {
    const display = window.matchMedia('(display-mode: standalone)')
    const updateDisplay = () => setStandalone(isStandalone(navigator, display.matches))
    const captureInstall = (event) => { event.preventDefault(); setInstallEvent(event) }
    const installed = () => { dismissHint(); setInstallEvent(null); setPanel(null) }
    const syncDismissed = (event) => {
      if (event.key === HOME_HINT_KEY || event.key === null) setDismissed(hintDismissed())
    }
    display.addEventListener('change', updateDisplay)
    window.addEventListener('beforeinstallprompt', captureInstall)
    window.addEventListener('appinstalled', installed)
    window.addEventListener('storage', syncDismissed)
    return () => {
      display.removeEventListener('change', updateDisplay)
      window.removeEventListener('beforeinstallprompt', captureInstall)
      window.removeEventListener('appinstalled', installed)
      window.removeEventListener('storage', syncDismissed)
    }
  }, [])

  const install = async () => {
    if (!installEvent) return
    try {
      await installEvent.prompt()
      await installEvent.userChoice
    } catch { /* Manual installation instructions remain available. */ }
    setInstallEvent(null)
  }

  return (
    <AccessContext.Provider value={{ platform, showHint: Boolean(platform) && !standalone && !dismissed, dismissHint, open: setPanel }}>
      {children}
      {panel && <AccessDialog mode={panel} platform={platform} canInstall={Boolean(installEvent)} onInstall={install} onClose={() => setPanel(null)} />}
    </AccessContext.Provider>
  )
}

export function HomeScreenHint() {
  const access = useContext(AccessContext)
  if (!access?.showHint) return null
  return (
    <div className="home-screen-hint">
      <div><span>다음에도 쉽게 찾아오세요.</span> <button type="button" className="home-screen-guide" onClick={() => access.open('home')}>홈 화면에 추가하기 ↗</button></div>
      <button type="button" className="home-screen-dismiss" onClick={access.dismissHint}>다시 보지 않기</button>
    </div>
  )
}

export function MobileAccessFooter() {
  const access = useContext(AccessContext)
  if (!access || access.platform) return null
  return <button className="mobile-access-footer" type="button" onClick={() => access.open('qr')}>모바일에서 사용하기 ↗</button>
}

function AccessDialog({ mode, platform, canInstall, onInstall, onClose }) {
  const dialog = useRef(null)
  const qr = mode === 'qr'

  useEffect(() => {
    const element = dialog.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element.showModal()
    return () => { element.close(); document.body.style.overflow = previousOverflow }
  }, [])

  return createPortal(
    <dialog className="mobile-access-dialog" ref={dialog} aria-labelledby="mobile-access-title" onClose={(event) => { if (!event.currentTarget.open) onClose() }}>
      <div className="mobile-access-heading">
        <h2 id="mobile-access-title">{qr ? '휴대폰에서도 AllEZ' : '홈 화면에서 바로 만나요'}</h2>
        <button className="mobile-access-close" type="button" aria-label="안내 닫기" onClick={() => dialog.current.close()}>닫기 ×</button>
      </div>
      {qr ? <>
        <p>휴대폰 카메라로 QR코드를 스캔하세요.</p>
        <img className="mobile-access-qr" src="/mobile-access-qr.png" width="220" height="220" alt="AllEZ 모바일 사이트 접속 QR코드" />
        <a className="mobile-access-url" href={MOBILE_SITE_URL}>{MOBILE_SITE_URL.replace('https://', '').replace(/\/$/, '')}</a>
        <p>접속 후 홈 화면에 추가하면 다음에도 쉽게 열 수 있어요.</p>
        <div className="mobile-access-steps"><p><strong>아이폰</strong> Safari 공유 → 홈 화면에 추가</p><p><strong>안드로이드</strong> Chrome 메뉴 → 홈 화면에 추가 또는 앱 설치</p></div>
      </> : <>
        <p>AllEZ 아이콘을 홈 화면에 두고, 다음에도 바로 열어보세요.</p>
        {canInstall && <button className="mobile-access-install" type="button" onClick={onInstall}>홈 화면에 추가</button>}
        {platform === 'ios' ? <ol className="mobile-access-steps">
          <li>Safari에서 아래쪽 또는 메뉴의 <strong>공유(↑)</strong> 버튼을 누르세요.</li>
          <li><strong>홈 화면에 추가</strong>를 선택하세요.</li>
          <li>이름을 확인하고 <strong>추가</strong>를 누르면 완료돼요.</li>
        </ol> : <ol className="mobile-access-steps">
          <li>Chrome의 <strong>메뉴(⋮)</strong>를 누르세요.</li>
          <li><strong>홈 화면에 추가</strong> 또는 <strong>앱 설치</strong>를 선택하세요.</li>
          <li><strong>설치</strong> 또는 <strong>추가</strong>를 누르면 완료돼요.</li>
        </ol>}
        <p className="mobile-access-note">메뉴가 보이지 않으면 {platform === 'ios' ? 'Safari' : 'Chrome'}에서 이 사이트를 열어주세요.</p>
      </>}
    </dialog>, document.body,
  )
}
