import './App.css'
import JobSearch from './JobSearch'

function App() {
  return (
    <div className="app-shell">
      <header>
        <a className="brand" href="/" aria-label="JobFit AX 홈">
          <img src="/jobfit-logo-lockup.svg" alt="JobFit AX" height="32" />
        </a>
        <span>채용공고 통합보기</span>
      </header>
      <main>
        <JobSearch />
      </main>
      <footer><b>JobFit AX</b><span>잡코리아와 사람인 공고를 한곳에서</span></footer>
    </div>
  )
}

export default App
