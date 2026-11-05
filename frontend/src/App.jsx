import './App.css'
import JobSearch from './JobSearch'
import AdminPage from './AdminPage'

function App() {
  if (['/admin', '/admin/'].includes(window.location.pathname)) return <AdminPage />
  return (
    <div className="app-shell">
      <header>
        <a className="brand" href="/" aria-label="AllEZ 홈">
          <span className="brand-wordmark"><span className="brand-all">All</span><span className="brand-ez">EZ</span></span>
        </a>
        <span>채용공고 통합보기</span>
      </header>
      <main>
        <JobSearch />
      </main>
      <footer className="site-footer">
        <div className="footer-product">
          <b className="brand-wordmark"><span className="brand-all">All</span><span className="brand-ez">EZ</span></b>
          <p>잡코리아와 사람인 공고를 한곳에서</p>
        </div>
        <div className="footer-company">
          <p className="footer-company-name">제작·운영 <strong>가마니연구소</strong></p>
          <p className="footer-slogan">필요한 것만 담아. 당신은 가만히.</p>
          <a className="footer-contact" href="mailto:gamani.labs@gmail.com"><span>서비스 문의 · 제휴 제안</span>gamani.labs@gmail.com</a>
          <small>© {new Date().getFullYear()} 가마니연구소</small>
        </div>
      </footer>
    </div>
  )
}

export default App
