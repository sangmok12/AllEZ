import { useState } from 'react'
import './App.css'
import JobSearch from './JobSearch'
import AdminPage from './AdminPage'

function App() {
  const [category, setCategory] = useState('jobs')
  if (['/admin', '/admin/'].includes(window.location.pathname)) return <AdminPage />
  return (
    <div className="app-shell">
      <header>
        <a className="brand" href="/" aria-label="AllEZ 홈">
          <span className="brand-wordmark"><span className="brand-all">All</span><span className="brand-ez">EZ</span></span>
        </a>
        <span>채용 · 알바 통합보기</span>
      </header>
      <main>
        <nav className="search-category" aria-label="검색 종류">
          <button type="button" aria-pressed={category === 'jobs'} onClick={() => setCategory('jobs')}>채용</button>
          <button type="button" aria-pressed={category === 'part_time'} onClick={() => setCategory('part_time')}>알바</button>
        </nav>
        <JobSearch key={category} partTime={category === 'part_time'} />
      </main>
      <footer className="site-footer">
        <div className="footer-company">
          <p className="footer-company-name">제작·운영 <strong>가마니연구소</strong></p>
          <p className="footer-slogan">필요한 것만 담아. 당신은 가만히.</p>
        </div>
        <div className="footer-details">
          <a className="footer-contact" href="mailto:gamani.labs@gmail.com"><span>문의 · 제휴</span>gamani.labs@gmail.com</a>
          <small>© {new Date().getFullYear()} 가마니연구소</small>
        </div>
      </footer>
    </div>
  )
}

export default App
