import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import './LargeSearchDialog.css'

export default function LargeSearchDialog({ estimate, onDecision }) {
  const ref = useRef(null)
  useEffect(() => {
    const element = ref.current
    element.showModal()
    return () => element.close()
  }, [])
  const time = estimate.minMinutes === estimate.maxMinutes ? `${estimate.minMinutes}분` : `${estimate.minMinutes}~${estimate.maxMinutes}분`
  return createPortal(<dialog ref={ref} className="large-search-dialog" aria-labelledby="large-search-title"
    onCancel={event => { event.preventDefault(); onDecision(false) }}>
    <h2 id="large-search-title">검색 결과가 약 {estimate.total.toLocaleString()}건이에요.</h2>
    <p>전체 공고를 모으는 데 약 <strong>{time}</strong>이 걸릴 수 있어요.<br />검색을 진행할까요?</p>
    <small>원본 사이트 응답과 검색 대기 상황에 따라 달라질 수 있어요. 진행 중에도 취소할 수 있어요.</small>
    <div><button type="button" onClick={() => onDecision(false)}>조건 변경</button><button type="button" autoFocus onClick={() => onDecision(true)}>계속 검색</button></div>
  </dialog>, document.body)
}
