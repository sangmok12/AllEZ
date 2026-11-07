import { useState } from 'react'
import './App.css'
import JobSearch from './JobSearch'
import AdminPage from './AdminPage'

function App() {
  const [category, setCategory] = useState('jobs')
  if (['/admin', '/admin/'].includes(window.location.pathname)) return <AdminPage />
  return (
    <div className={`app-shell theme-${category}`}>
      <header>
        <a className="brand" href="/" aria-label="AllEZ 홈">
          <span className="brand-wordmark"><span className="brand-all">All</span><span className="brand-ez">EZ</span></span>
        </a>
        <nav className="search-category" aria-label="검색 사이트">
          <button className="category-jobs" type="button" aria-pressed={category === 'jobs'} onClick={() => setCategory('jobs')}>사람인 · 잡코리아</button>
          <button className="category-part-time" type="button" aria-pressed={category === 'part_time'} onClick={() => setCategory('part_time')}>알바몬 · 알바천국</button>
        </nav>
      </header>
      <main>
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
