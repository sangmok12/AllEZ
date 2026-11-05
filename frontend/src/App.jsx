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
      <footer><b><span className="brand-wordmark"><span className="brand-all">All</span><span className="brand-ez">EZ</span></span></b><span>잡코리아와 사람인 공고를 한곳에서</span></footer>
    </div>
  )
}

export default App
